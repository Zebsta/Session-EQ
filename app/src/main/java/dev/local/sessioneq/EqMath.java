package dev.local.sessioneq;

public final class EqMath {
    private EqMath() {}
    public static final float[] CENTERS = {31,43,63,87,125,175,250,350,500,700,1000,1400,2000,2800,4000,5600,8000,11200,16000,22000};
    public static final String[] LABELS = {"31","43","63","87","125","175","250","350","500","700","1 k","1.4 k","2 k","2.8 k","4 k","5.6 k","8 k","11.2 k","16 k","22 k"};
    public static final float[] ROCK = {5.4f,5.1f,4.5f,1.3f,-3.6f,-4.8f,-6.6f,-5f,-2.7f,-.8f,2.1f,3.7f,6f,6.6f,7.5f,7.6f,7.8f,7.9f,8.1f,8.1f};
    public static float clamp(float v, float lo, float hi) { return Float.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : 0; }
    // DynamicsProcessing expects upper band boundaries, rather than center frequencies.
    // The final band extends to Nyquist (the device ignores bins above it).
    public static float cutoff(int band) {
        return band == CENTERS.length - 1 ? 24000f : (float)Math.sqrt(CENTERS[band] * CENTERS[band+1]);
    }
    public static float inputGain(float preamp, float balance, int channel, boolean auto, float[] gains) {
        float peak = 0;
        for (float gain : gains) peak = Math.max(peak, gain);
        float input = preamp - (auto ? Math.max(0, preamp + peak) : 0);
        float factor = channel == 0 ? 1 - Math.max(0, balance) : 1 + Math.min(0, balance);
        return input + (factor <= 0 ? -100 : (float)(20 * Math.log10(factor)));
    }
}
