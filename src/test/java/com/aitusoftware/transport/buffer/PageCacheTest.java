package com.aitusoftware.transport.buffer;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class PageCacheTest
{
    private static final int PAGE_SIZE = 4096;
    private static final int MESSAGE_SIZE = PAGE_SIZE / 23;
    private static final long PADDED_MESSAGE_SIZE = PageHeader.getAlignedPosition(MESSAGE_SIZE);
    private static final long WASTED_PAGE_SPACE = 64;
    private static final int MESSAGE_COUNT = 128;
    private static final int PAGE_COUNT = 128 / 23;

    private final PageCache pageCache = PageCache.create(Fixtures.tempDirectory(), PAGE_SIZE);
    private final ByteBuffer message = ByteBuffer.allocate(MESSAGE_SIZE);

    @Test
    public void shouldAppendDataOverSeveralPages() throws Exception
    {
        for (int i = 0; i < MESSAGE_COUNT; i++)
        {
            tagMessage(message, i);

            pageCache.append(message);
        }

        assertThat(pageCache.estimateTotalLength(), is(MESSAGE_COUNT * PADDED_MESSAGE_SIZE + (PAGE_COUNT + 1) * WASTED_PAGE_SPACE));
    }

    private static void tagMessage(final ByteBuffer target, final int messageId)
    {
        target.clear();
        while (target.remaining() != 0)
        {
            target.put((byte) messageId);
        }
        target.flip();
    }
}