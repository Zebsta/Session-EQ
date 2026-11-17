import tempfile
import unittest
from pathlib import Path
import numpy as np
from scipy.io import wavfile
from scipy import signal
from compare import analyze, levels


class ComparisonTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.rate = 16000
        rng = np.random.default_rng(42)
        self.audio = signal.sosfilt(signal.butter(4, 2500, fs=self.rate, output='sos'), rng.normal(0, .08, self.rate*6))
        self.audio = np.column_stack([self.audio, self.audio*.8])

    def write(self, name, audio, rate=None):
        path = self.root/name
        wavfile.write(path, rate or self.rate, audio.astype(np.float32))
        return path

    def test_known_delay_and_gain(self):
        a = self.write('a.wav', self.audio)
        b = self.write('b.wav', np.pad(self.audio*2, ((1234,0),(0,0))))
        summary, _ = analyze(a,b)
        self.assertEqual(summary['test_delay_samples'],1234)
        self.assertAlmostEqual(summary['test_minus_reference_rms_db'],6.0205999,places=4)
        self.assertAlmostEqual(summary['test_gain_to_match_reference'],.5,places=5)
        self.assertLess(summary['level_matched_residual_db_relative_to_reference'],-100)

    def test_negative_delay(self):
        a = self.write('a.wav', np.pad(self.audio,((513,0),(0,0))))
        b = self.write('b.wav',self.audio)
        summary,_ = analyze(a,b)
        self.assertEqual(summary['test_delay_samples'],-513)

    def test_rate_conversion(self):
        a = self.write('a.wav', self.audio)
        b = self.write('b.wav', signal.resample_poly(self.audio,3,2),24000)
        summary,_ = analyze(a,b)
        self.assertEqual(summary['original_test_sample_rate'],24000)
        self.assertGreater(summary['correlation'],.99)

    def test_silence_and_unrelated_audio_refused(self):
        a = self.write('a.wav',self.audio)
        silence = self.write('silent.wav',np.zeros_like(self.audio))
        with self.assertRaisesRegex(ValueError,'Silent'):analyze(a,silence)
        noise = self.write('noise.wav',np.random.default_rng(2).normal(0,.1,self.audio.shape))
        with self.assertRaisesRegex(ValueError,'Alignment unreliable'):analyze(a,noise)

    def test_full_scale_metric(self):
        self.assertEqual(levels(np.array([[-1.0],[.0],[1.0]]))['near_full_scale_fraction'],2/3)


if __name__ == '__main__':unittest.main()
