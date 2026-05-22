package com.ece420.lab6;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.IOException;
import java.lang.Math;


public class CameraActivity extends Activity implements SurfaceHolder.Callback{

    // UI Variable
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private SurfaceView surfaceView2;
    private SurfaceHolder surfaceHolder2;
    private TextView textHelper;
    // Camera Variable
    private Camera camera;
    boolean previewing = false;
    private int width = 640;
    private int height = 480;
    private int K = 3;
    private int L = 30;
    private int Lc = 90;
    private int threshold = 25;
    private float classification_threshold = 0.5f;
    private float min_weight = 0.05f;
    private float[][] weight;
    private float[][][] centroid;
    private boolean initialized = false;

    private int backColor = 0xFF000000;
    private boolean cameraSwitch = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFormat(PixelFormat.UNKNOWN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_camera);
        super.setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Setup Surface View handler
        surfaceView = (SurfaceView)findViewById(R.id.ViewOrigin);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        surfaceView2 = (SurfaceView)findViewById(R.id.ViewBackSeg);
        surfaceHolder2 = surfaceView2.getHolder();

        findViewById(R.id.redButton).setOnClickListener(v -> backColor = 0xFFFF0000);
        findViewById(R.id.orangeButton).setOnClickListener(v -> backColor = 0xFFFF6600);
        findViewById(R.id.yellowButton).setOnClickListener(v -> backColor = 0xFFFFFF00);
        findViewById(R.id.greenButton).setOnClickListener(v -> backColor = 0xFF008000);
        findViewById(R.id.blueButton).setOnClickListener(v -> backColor = 0xFF0000FF);
        findViewById(R.id.indigoButton).setOnClickListener(v -> backColor = 0xFF4B0082);
        findViewById(R.id.violetButton).setOnClickListener(v -> backColor = 0xFFEE82EE);
        findViewById(R.id.blackButton).setOnClickListener(v -> backColor = 0xFF000000);

        findViewById(R.id.cameraSwitchButton).setOnClickListener(v -> {
            cameraSwitch = !cameraSwitch;

            if (camera != null) {
                camera.stopPreview();
                camera.setPreviewCallback(null);
                camera.release();
                camera = null;
                previewing = false;
            }

            surfaceCreated(surfaceHolder);
        });
    }


    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // Must have to override native method
        return;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if(!previewing) {

            if(!cameraSwitch) {
                camera = Camera.open();
            }else {
                camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
            }

            if (camera != null) {
                try {
                    // Modify Camera Settings
                    Camera.Parameters parameters = camera.getParameters();
                    parameters.setPreviewSize(width, height);
                    // Following lines could log possible camera resolutions, including
                    // 2592x1944;1920x1080;1440x1080;1280x720;640x480;352x288;320x240;176x144;
                    // List<Camera.Size> sizes = parameters.getSupportedPictureSizes();
                    // for(int i=0; i<sizes.size(); i++) {
                    //     int height = sizes.get(i).height;
                    //     int width = sizes.get(i).width;
                    //     Log.d("size: ", Integer.toString(width) + ";" + Integer.toString(height));
                    // }
                    camera.setParameters(parameters);
                    camera.setDisplayOrientation(90);
                    camera.setPreviewDisplay(surfaceHolder);
                    camera.setPreviewCallback(new PreviewCallback() {
                        public void onPreviewFrame(byte[] data, Camera camera)
                        {
                            // Lock canvas
                            Canvas canvas = surfaceHolder2.lockCanvas(null);
                            // Where Callback Happens, camera preview frame ready
                            onCameraFrame(canvas,data);
                            // Unlock canvas
                            surfaceHolder2.unlockCanvasAndPost(canvas);
                        }
                    });
                    camera.startPreview();
                    previewing = true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // Cleaning Up
        if (camera != null && previewing) {
            camera.stopPreview();
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
            previewing = false;
        }
    }

    // Camera Preview Frame Callback Function
    protected void onCameraFrame(Canvas canvas, byte[] data) {

        Matrix matrix = new Matrix();
        matrix.postRotate(90);

        if (cameraSwitch) {
            matrix.postScale(1, -1);
        }

        int retData[] = new int[width * height];

        byte[] backsegData = backSeg(data, width, height);
        retData = yuv2rgb(backsegData);

        // Create ARGB Image, rotate and draw
        Bitmap bmp = Bitmap.createBitmap(retData, width, height, Bitmap.Config.ARGB_8888);
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
        canvas.drawBitmap(bmp, new Rect(0,0, height, width), new Rect(0,0, canvas.getWidth(), canvas.getHeight()),null);
    }

    // Helper function to convert YUV to RGB
    public int[] yuv2rgb(byte[] data){
        final int frameSize = width * height;
        int[] rgb = new int[frameSize];

        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & ((int) data[yp])) - 16;
                y = y<0? 0:y;

                if ((i & 1) == 0) {
                    v = (0xff & data[uvp++]) - 128;
                    u = (0xff & data[uvp++]) - 128;
                }

                if(((data[yp] & 0xFF) == 0)) {
                    rgb[yp] = backColor;
                    continue;
                }

                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                r = r<0? 0:r;
                r = r>262143? 262143:r;
                g = g<0? 0:g;
                g = g>262143? 262143:g;
                b = b<0? 0:b;
                b = b>262143? 262143:b;

                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
        return rgb;
    }

    public byte[] gaussianBlurY(byte[] data, int width, int height) {

        int size = width * height;
        byte[] result = new byte[data.length];

        for (int i = size; i < data.length; i++) {
            result[i] = data[i];
        }

        int[][] kernel = {
                {1, 2, 1},
                {2, 4, 2},
                {1, 2, 1}
        };

        int kernelSum = 16;

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {

                int sum = 0;

                for (int j = -1; j <= 1; j++) {
                    for (int i = -1; i <= 1; i++) {
                        int idx = (y + j) * width + (x + i);
                        int Y = data[idx] & 0xFF;
                        sum += Y * kernel[j + 1][i + 1];
                    }
                }

                int blurred = sum / kernelSum;
                result[y * width + x] = (byte) blurred;
            }
        }

        return result;
    }

    // Function for Background Segmentation
    public byte[] backSeg(byte[] data2, int width, int height){
        byte[] result = new byte[data2.length];
        int size = height * width;

        byte[] data = gaussianBlurY(data2, width, height);


        if(!initialized) {

            weight = new float[size][K];
            centroid = new float[size][K][3];

            initialized = true;

            for(int i = 0; i < size; i++) {

                int Y = data[i] & 0xFF;

                int row = i/width;
                int col = i%width;
                int idxu = size + row/2 * width/2 + col/2;
                int idxv = size + size/4 + row/2 * width/2 + col/2;
                int U = data[idxu] & 0xFF;
                int V = data[idxv] & 0xFF;

                for(int j = 0; j < K; j++) {

                    weight[i][j] = 1.0f / K;

                    centroid[i][j][0] = Y;
                    centroid[i][j][1] = U;
                    centroid[i][j][2] = V;

                }
            }
        }

        for(int p = 0; p < size; p++) {
            int Y = data[p] & 0xFF;

            int row = p/width;
            int col = p%width;
            int idxu = size + row/2 * width/2 + col/2;
            int idxv = size + size/4 + row/2 * width/2 + col/2;
            int U = data[idxu] & 0xFF;
            int V = data[idxv] & 0xFF;

            //step 1:
            int midx = -1;

            int a = 0, b = 1, c = 2;

            if(weight[p][a] < weight[p][c]) {
                int temp = c;
                c = a;
                a = temp;
            }

            if(weight[p][a] < weight[p][b]) {
                int temp = b;
                b = a;
                a = temp;
            }

            if(weight[p][b] < weight[p][c]) {
                int temp = c;
                c = b;
                b = temp;
            }

            int[] indices = {a, b, c};

            for(int l = 0; l < K; l++) {
                int k = indices[l];

                float distance = Math.abs(Y - centroid[p][k][0]) + Math.abs(U - centroid[p][k][1]) + Math.abs(V - centroid[p][k][2]);

                if(distance < threshold) {
                    midx = k;
                    break;
                }
            }

            //step 2:
            if(midx != -1) {
                for(int l = 0; l < K; l++) {
                    int k = indices[l];

                    if (k == midx) {
                        weight[p][k] = weight[p][k] + (1.0f / L) * (1.0f - weight[p][k]);
                    } else {
                        weight[p][k] = weight[p][k] + (1.0f / L) * (0.0f - weight[p][k]);
                    }
                }

                centroid[p][midx][0] = centroid[p][midx][0] + (1.0f / Lc) * (Y - centroid[p][midx][0]);
                centroid[p][midx][1] = centroid[p][midx][1] + (1.0f / Lc) * (U - centroid[p][midx][1]);
                centroid[p][midx][2] = centroid[p][midx][2] + (1.0f / Lc) * (V - centroid[p][midx][2]);

            }else {
                weight[p][indices[K - 1]] = min_weight;
                centroid[p][indices[K - 1]][0] = Y;
                centroid[p][indices[K - 1]][1] = U;
                centroid[p][indices[K - 1]][2] = V;
            }

            a = 0;
            b = 1;
            c = 2;

            if(weight[p][a] < weight[p][c]) {
                int temp = c;
                c = a;
                a = temp;
            }

            if(weight[p][a] < weight[p][b]) {
                int temp = b;
                b = a;
                a = temp;
            }

            if(weight[p][b] < weight[p][c]) {
                int temp = c;
                c = b;
                b = temp;
            }

            int[] index = {a, b, c};

            //step 3:
            float sum = 0.0f;

            for(int m = 0; m < K; m++) {
                sum += weight[p][m];
            }

            for(int n = 0; n < K; n++) {
                weight[p][n] /= sum;
            }

            //step 4:
            int pixel_val = 0;

            if (midx !=-1) {
                int idxc = 0;

                for(int m = 0; m < K; m++) {
                    if(index[m] == midx) {
                        idxc = m;
                        break;
                    }
                }

                float s = 0.0f;

                for(int n = 0; n < idxc; n++) {
                    s += weight[p][index[n]];
                }

                if(s > classification_threshold) {
                    pixel_val = 255;
                }else{
                    pixel_val = 0;
                }

            } else {
                pixel_val = 255;
            }

            result[p] = (byte)pixel_val;
        }

        result = postProcessing(result, width, height, 6000);

        return result;
    }

    public byte[] majorityFilter(byte[] data, int width, int height) {

        byte[] result = new byte[data.length];

        int size = width * height;

        for (int i = size; i < data.length; i++) {
            result[i] = data[i];
        }

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {

                int countWhite = 0;

                for (int j = -1; j <= 1; j++) {
                    for (int i = -1; i <= 1; i++) {
                        int idx = (y + j) * width + (x + i);
                        if ((data[idx] & 0xFF) > 0) {
                            countWhite++;
                        }
                    }
                }

                if (countWhite >= 5) {
                    result[y * width + x] = (byte)255;
                } else {
                    result[y * width + x] = (byte)0;
                }
            }
        }

        return result;
    }

    public byte[] postProcessing(byte[] data, int width, int height, int a) {

        byte[] result = new byte[data.length];

        for (int p = 0; p < width * height; p++) {
            result[p] = data[p];
        }

        //look for white areas smaller than a certain threshold to remove noise

        int[] queue = new int[width * height];
        boolean[] visited = new boolean[width * height];
        byte[] postData = new byte[data.length];

        for (int i = 0; i < height; i++) {

            for (int j = 0; j < width; j++) {
                if (result[i * width + j] != 0 && !visited[i * width + j]) {
                    int front = 0;
                    int back = 0;
                    int start = i * width + j;

                    queue[back] = start;
                    back += 1;
                    visited[start] = true;

                    while (front < back) {
                        int pixel = queue[front];
                        front += 1;

                        int x = pixel / width;
                        int y = pixel % width;

                        if (x + 1 < height && result[(x + 1) * width + y] != 0 && !visited[(x + 1) * width + y]) {
                            queue[back] = (x + 1) * width + y;
                            back += 1;
                            visited[(x + 1) * width + y] = true;
                        }

                        if (x - 1 >= 0 && result[(x - 1) * width + y] != 0 && !visited[(x - 1) * width + y]) {
                            queue[back] = (x - 1) * width + y;
                            back += 1;
                            visited[(x - 1) * width + y] = true;
                        }

                        if (y + 1 < width && result[x * width + (y + 1)] != 0 && !visited[x * width + (y + 1)]) {
                            queue[back] = x * width + (y + 1);
                            back += 1;
                            visited[x * width + (y + 1)] = true;
                        }

                        if (y - 1 >= 0 && result[x * width + (y - 1)] != 0 && !visited[x * width + (y - 1)]) {
                            queue[back] = x * width + (y - 1);
                            back += 1;
                            visited[x * width + (y - 1)] = true;
                        }

                        if (x + 1 < height && y + 1 < width && result[(x + 1) * width + (y + 1)] != 0 && !visited[(x + 1) * width + (y + 1)]) {
                            queue[back] = (x + 1) * width + (y + 1);
                            back += 1;
                            visited[(x + 1) * width + (y + 1)] = true;
                        }

                        if (x + 1 < height && y - 1 >= 0 && result[(x + 1) * width + (y - 1)] != 0 && !visited[(x + 1) * width + (y - 1)]) {
                            queue[back] = (x + 1) * width + (y - 1);
                            back += 1;
                            visited[(x + 1) * width + (y - 1)] = true;
                        }

                        if (x - 1 >= 0 && y + 1 < width && result[(x - 1) * width + (y + 1)] != 0 && !visited[(x - 1) * width + (y + 1)]) {
                            queue[back] = (x - 1) * width + (y + 1);
                            back += 1;
                            visited[(x - 1) * width + (y + 1)] = true;
                        }

                        if (x - 1 >= 0 && y - 1 >= 0 && result[(x - 1) * width + (y - 1)] != 0 && !visited[(x - 1) * width + (y - 1)]) {
                            queue[back] = (x - 1) * width + (y - 1);
                            back += 1;
                            visited[(x - 1) * width + (y - 1)] = true;
                        }
                    }

                    for (int b = 0; b < back; b++) {
                        if (back < a) {
                            postData[queue[b]] = (byte) 0;
                        } else {
                            postData[queue[b]] = (byte) 255;
                        }
                    }
                }
            }
        }

        for (int i = width * height; i < data.length; i++) {
            postData[i] = (byte) 128;
        }

        //apply majority filter to reduce noise

        postData = majorityFilter(postData, width, height);

        return postData;
    }
}
