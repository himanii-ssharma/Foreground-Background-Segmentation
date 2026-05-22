# Foreground-Background-Segmentation
# Adaptive Foreground-Background Segmentation — Android App

Real-time background segmentation and swapping app for Android, built from 
scratch in Java using the Butler et al. (2005) adaptive clustering algorithm.

---

## Overview
This project implements adaptive foreground/background segmentation to separate 
moving objects from a pseudo-stationary background in real time. Unlike naive 
background subtraction, the algorithm models each pixel as a set of weighted 
clusters that update every frame, handling lighting changes and dynamic backgrounds.

Users can replace the detected background with a solid color in real time via 
an Android app interface.

---

## Algorithm
Implements the **Butler et al. (2005)** adaptive clustering background model:

1. **Cluster Matching** — Each pixel is compared to K weighted clusters using 
   Manhattan distance. If no match is found, the lowest-weight cluster is replaced.
2. **Adaptation** — Matched cluster centroids and weights update every frame via 
   separate learning rates.
3. **Normalization** — Cluster weights are normalized to represent background 
   probability.
4. **Classification** — Pixels are classified as foreground or background based 
   on cumulative cluster weights.

---

## Optimizations

### Preprocessing
- **Gaussian Blur (3×3)** applied to the luminance (Y) channel to reduce sensor 
  noise and pixel fluctuations before segmentation

### Postprocessing
- **Connected Components Analysis (CCA)** — BFS-based 8-connected component 
  search removes foreground blobs below a size threshold
- **Majority Filter** — 3×3 neighborhood voting smooths edges, fills holes, 
  and removes isolated noise pixels

### Dual Learning Rate
Two separate learning rates decouple background weight updates from centroid 
color updates, improving stability under dynamic lighting without absorbing 
the foreground into the background.

| Parameter | Value |
|---|---|
| K (clusters) | 3 |
| L (weight learning rate) | 30 |
| Lc (centroid learning rate) | 90 |
| Distance Threshold | 25 |
| Classification Threshold | 0.5 |

---

## Results

Validated against **OpenCV MOG2** as a baseline with concurrent same-input testing:

| Metric | Our Algorithm | OpenCV MOG2 |
|---|---|---|
| FPS (Android) | 5 | 80 |
| False Negative Rate | 4–6% | baseline |
| False Positive Rate | 13–15% | baseline |

The higher false positive rate is attributable to our algorithm's design choice 
to fully fill foreground silhouettes, whereas MOG2 leaves holes in the foreground mask.

---

## Tech Stack
- Java / Android Studio
- YUV → RGB color space conversion
- BFS-based connected component analysis
- Gaussian and majority filter postprocessing

---

## Technologies & Tools
- Java
- Android Studio
- OpenCV MOG2 (baseline comparison)
- ECE 420 — Embedded DSP Laboratory, UIUC
