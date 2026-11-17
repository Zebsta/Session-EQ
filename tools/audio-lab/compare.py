#!/usr/bin/env python3
"""Compare repeated digital captures; spectral ratios are not calibrated EQ responses."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import numpy as np
from scipy import signal
from scipy.io import wavfile


def read_audio(path):
    rate, samples = wavfile.read(path)
    if samples.ndim == 1:
        samples = samples[:, None]
    if samples.ndim != 2 or not len(samples):
        raise ValueError('Empty or unsupported WAV')
    if np.issubdtype(samples.dtype, np.signedinteger):
        audio = samples.astype(np.float64) / float(2 ** (samples.dtype.itemsize * 8 - 1))
    elif samples.dtype == np.uint8:
        audio = (samples.astype(np.float64) - 128) / 128
    elif np.issubdtype(samples.dtype, np.floating):
        audio = samples.astype(np.float64)
    else:
        raise ValueError('Unsupported WAV encoding')
    if not np.all(np.isfinite(audio)):
        raise ValueError('Non-finite WAV samples')
    return rate, audio


def db(value):
    return float(20 * np.log10(max(float(value), 1e-15)))


def levels(audio):
    return {'rms_dbfs': db(np.sqrt(np.mean(audio ** 2))),
            'sample_peak_dbfs': db(np.max(np.abs(audio))),
            'near_full_scale_fraction': float(np.mean(np.abs(audio) >= 32767 / 32768))}


def overlap(reference, test, lag):
    if lag >= 0:
        test = test[lag:]
    else:
        reference = reference[-lag:]
    size = min(len(reference), len(test))
    return reference[:size], test[:size]


def resample(audio, old_rate, new_rate):
    divisor = math.gcd(old_rate, new_rate)
    return signal.resample_poly(audio, new_rate // divisor, old_rate // divisor, axis=0)


def best_lag(reference, test, bound):
    x = reference - np.mean(reference)
    y = test - np.mean(test)
    correlation = signal.correlate(y, x, method='fft')
    lags = signal.correlation_lags(len(y), len(x))
    valid = np.abs(lags) <= bound
    return int(lags[valid][np.argmax(correlation[valid])])


def analyze(reference_path, test_path, max_lag=15, seconds=60, trim=1):
    rate, reference = read_audio(reference_path)
    test_rate, test = read_audio(test_path)
    original_test_rate = test_rate
    if reference.shape[1] != test.shape[1]:
        raise ValueError('Channel counts differ; do not silently downmix captures')
    if test_rate != rate:
        test = resample(test, test_rate, rate)
    reference = reference[:int(seconds * rate)]
    test = test[:int(seconds * rate)]
    if min(len(reference), len(test)) < rate * 3:
        raise ValueError('At least three seconds of audio are required')
    if min(np.sqrt(np.mean(reference ** 2)), np.sqrt(np.mean(test ** 2))) < 1e-7:
        raise ValueError('Silent or nearly silent capture; check capture policy/source and volume')
    channel = int(np.argmax(np.mean(reference ** 2, axis=0)))
    coarse_rate = 2000
    x = resample(reference[:rate*30, channel], rate, coarse_rate)
    y = resample(test[:rate*30, channel], rate, coarse_rate)
    coarse = round(best_lag(x, y, round(max_lag * coarse_rate)) * rate / coarse_rate)
    a, b = overlap(reference, test, coarse)
    fine = best_lag(a[:rate*8, channel], b[:rate*8, channel], max(2, round(rate/coarse_rate)*4))
    lag = coarse + fine
    a, b = overlap(reference, test, lag)
    edge = round(trim * rate)
    if edge:
        a, b = a[edge:-edge], b[edge:-edge]
    if len(a) < rate:
        raise ValueError('Insufficient common audio after alignment and trimming')
    correlation = float(np.corrcoef(a[:, channel], b[:, channel])[0, 1])
    if not np.isfinite(correlation) or correlation < .3:
        raise ValueError('Alignment unreliable: captures may contain different fragments or routing')
    gain = float(np.sum(a * b) / np.sum(b * b))
    residual = a - b * gain
    ref_levels, test_levels = levels(a), levels(b)
    frequency, p_ref = signal.welch(a, fs=rate, axis=0, nperseg=min(16384, len(a)))
    _, p_test = signal.welch(b, fs=rate, axis=0, nperseg=min(16384, len(b)))
    p_ref, p_test = np.mean(p_ref, axis=1), np.mean(p_test, axis=1)
    good = p_ref > np.max(p_ref) * 1e-8
    ratio = np.full_like(p_ref, np.nan)
    ratio[good] = 10 * np.log10(np.maximum(p_test[good], 1e-30) / p_ref[good])
    summary = {
        'reference': str(reference_path), 'test': str(test_path),
        'reference_sha256': hashlib.sha256(Path(reference_path).read_bytes()).hexdigest(),
        'test_sha256': hashlib.sha256(Path(test_path).read_bytes()).hexdigest(),
        'sample_rate': rate, 'original_test_sample_rate': original_test_rate,
        'channels': int(a.shape[1]), 'aligned_seconds': len(a)/rate,
        'test_delay_samples': lag, 'test_delay_seconds': lag/rate,
        'correlation': correlation, 'reference_levels': ref_levels, 'test_levels': test_levels,
        'test_minus_reference_rms_db': test_levels['rms_dbfs']-ref_levels['rms_dbfs'],
        'test_gain_to_match_reference': gain,
        'level_matched_residual_db_relative_to_reference': db(np.sqrt(np.mean(residual**2)) / np.sqrt(np.mean(a**2))),
        'limitations': ['No clock-drift or fractional-delay correction; residual includes those errors.',
                        'Music spectrum ratio includes decoder, routing, limiter and gain differences; it is not calibrated filter response.',
                        'Sample peak and RMS are not true-peak or LUFS measurements.',
                        'Validate the capture point with a deliberately changed EQ band before comparing players.']}
    return summary, (frequency, p_ref, p_test, ratio, gain)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('reference', type=Path)
    parser.add_argument('test', type=Path)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--max-lag', type=float, default=15)
    parser.add_argument('--seconds', type=float, default=60)
    args = parser.parse_args()
    # Metadata must describe the same capture method; do not compare unlike routing silently.
    metadata = []
    for path in (args.reference, args.test):
        sidecar = path.with_suffix('.json')
        metadata.append(json.loads(sidecar.read_text(encoding='utf-8-sig')) if sidecar.exists() else None)
    if all(metadata) and metadata[0].get('source') != metadata[1].get('source'):
        raise SystemExit('Capture sources differ; record both conditions with the same source')
    try:
        summary, data = analyze(args.reference, args.test, args.max_lag, args.seconds)
    except ValueError as error:
        raise SystemExit(str(error))
    args.out.mkdir(parents=True, exist_ok=True)
    summary['capture_metadata'] = metadata
    (args.out/'comparison.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2)+'\n')
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    frequency, ref, test, ratio, gain = data
    fig, axes = plt.subplots(2, 1, figsize=(11, 8), sharex=True)
    use = (frequency >= 20) & (frequency <= min(20000, summary['sample_rate']/2))
    axes[0].semilogx(frequency[use], 10*np.log10(np.maximum(ref[use], 1e-30)), label='Reference', alpha=.8)
    axes[0].semilogx(frequency[use], 10*np.log10(np.maximum(test[use], 1e-30)), label='Test', alpha=.7)
    axes[0].set_ylabel('Power density, dBFS/Hz'); axes[0].legend()
    axes[1].semilogx(frequency[use], ratio[use], label='Original level', alpha=.7)
    axes[1].semilogx(frequency[use], ratio[use]+db(abs(gain)), label='Level matched', alpha=.7)
    axes[1].axhline(0, color='gray', linewidth=.8)
    axes[1].set_ylabel('Test / reference, dB');axes[1].set_xlabel('Frequency, Hz');axes[1].legend()
    for axis in axes: axis.grid(True, which='both', alpha=.25)
    fig.suptitle('Digital capture comparison — spectrum ratio, not calibrated EQ response')
    fig.tight_layout();fig.savefig(args.out/'comparison.png', dpi=160);plt.close(fig)
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
