package gatesofhumanity.ephemeris;

import java.io.*;

/**
 * Longitude of one body as consecutive Chebyshev series, in the format written by
 * {@code gatesofhumanity.EphemerisGenerator}.
 */
final class ChebyshevTable {
    private static final int MAGIC = 0x48444550;
    private static final int FORMAT_VERSION = 1;

    final double jdStart_;
    final double jdEnd_;
    private final int segmentDays_;
    private final double[][] coefficients_;

    private ChebyshevTable(double jdStart, int segmentDays, double[][] coefficients) {
        jdStart_ = jdStart;
        jdEnd_ = jdStart + coefficients.length * (double) segmentDays;
        segmentDays_ = segmentDays;
        coefficients_ = coefficients;
    }

    static ChebyshevTable read(InputStream stream)
    throws IOException {
        var in = new DataInputStream(new BufferedInputStream(stream));
        if (in.readInt() != MAGIC || in.readInt() != FORMAT_VERSION) {
            throw new IOException("Not an ephemeris table of format version " + FORMAT_VERSION);
        }
        var jd_start = in.readDouble();
        var segment_days = in.readInt();
        var degree = in.readInt();
        var coefficients = new double[in.readInt()][degree + 1];
        for (var c : coefficients) {
            c[0] = in.readDouble();
            for (var k = 1; k <= degree; ++k) {
                c[k] = in.readFloat();
            }
        }
        return new ChebyshevTable(jd_start, segment_days, coefficients);
    }

    double longitude(double jd) {
        if (!(jd >= jdStart_ && jd <= jdEnd_)) {
            throw new IllegalArgumentException("Julian day " + jd + " is outside of the ephemeris range " + jdStart_ + " to " + jdEnd_);
        }
        var segment = Math.min((int) ((jd - jdStart_) / segmentDays_), coefficients_.length - 1);
        var x = 2.0 * (jd - jdStart_ - segment * (double) segmentDays_) / segmentDays_ - 1.0;
        var c = coefficients_[segment];
        var b1 = 0.0;
        var b2 = 0.0;
        for (var k = c.length - 1; k >= 1; --k) {
            var b0 = 2.0 * x * b1 - b2 + c[k];
            b2 = b1;
            b1 = b0;
        }
        var result = (x * b1 - b2 + c[0]) % 360.0;
        return result < 0.0 ? result + 360.0 : result;
    }
}
