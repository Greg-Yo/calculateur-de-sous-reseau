package com.coco.subnet.config;

import com.coco.subnet.controller.PayloadTooLargeException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

import java.io.IOException;

/** Compte les octets lus et s'arrête au-delà de la limite (requêtes sans Content-Length). */
final class LimitedServletInputStream extends ServletInputStream {

    private final ServletInputStream delegate;
    private final long maxBytes;
    private long count;

    LimitedServletInputStream(ServletInputStream delegate, long maxBytes) {
        this.delegate = delegate;
        this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
        int b = delegate.read();
        if (b != -1) {
            add(1);
        }
        return b;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int n = delegate.read(buffer, offset, length);
        if (n > 0) {
            add(n);
        }
        return n;
    }

    private void add(long n) throws PayloadTooLargeException {
        count += n;
        if (count > maxBytes) {
            throw new PayloadTooLargeException(maxBytes);
        }
    }

    @Override
    public boolean isFinished() {
        return delegate.isFinished();
    }

    @Override
    public boolean isReady() {
        return delegate.isReady();
    }

    @Override
    public void setReadListener(ReadListener listener) {
        delegate.setReadListener(listener);
    }
}
