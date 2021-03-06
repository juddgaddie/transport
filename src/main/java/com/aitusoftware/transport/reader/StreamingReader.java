package com.aitusoftware.transport.reader;

import com.aitusoftware.transport.buffer.Offsets;
import com.aitusoftware.transport.buffer.Page;
import com.aitusoftware.transport.buffer.PageCache;
import com.aitusoftware.transport.buffer.Record;
import com.aitusoftware.transport.buffer.Slice;
import com.aitusoftware.transport.threads.Idler;
import com.aitusoftware.transport.threads.Idlers;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class StreamingReader
{
    private final PageCache pageCache;
    private final RecordHandler recordHandler;
    private final boolean tail;
    private final Idler idler = Idlers.staticPause(1, TimeUnit.MILLISECONDS);
    private final AtomicLong messageCount = new AtomicLong();
    private long localMessageCount;
    private int pageNumber = 0;
    private int position = 0;
    private Page page;

    public StreamingReader(
            final PageCache pageCache, final RecordHandler recordHandler, final boolean tail)
    {
        this.pageCache = pageCache;
        this.recordHandler = recordHandler;
        this.tail = tail;
    }

    public void process()
    {
        while (!Thread.currentThread().isInterrupted())
        {
            if (!processRecord())
            {
                if (!tail)
                {
                    return;
                }
                idler.idle();
            }
            else
            {
                idler.reset();
            }
        }
    }

    public boolean processRecord()
    {
        if (page == null)
        {
            if (!pageCache.isPageAvailable(pageNumber))
            {
                return false;
            }
            page = pageCache.getPage(pageNumber);
        }

        final int header = page.header(position);
        final int recordLength = Page.recordLength(header);
        if (Page.isReady(header))
        {
            final Slice slice = pageCache.slice(pageNumber, position, recordLength);
            try
            {
                recordHandler.onRecord(slice.buffer(), pageNumber, position);
            }
            finally
            {
                slice.release();
            }
            localMessageCount++;
            messageCount.lazySet(localMessageCount);
            position += recordLength + Record.HEADER_LENGTH;
            position = Offsets.getAlignedPosition(position);
            if (position >= pageCache.getPageSize())
            {
                advancePage();
            }
            return true;
        }
        else if (Page.isEof(header))
        {
            advancePage();
        }
        else if (!tail)
        {
            return false;
        }

        return pageCache.isPageAvailable(pageNumber);
    }

    private void advancePage()
    {
        if (page != null)
        {
            page.releaseReference();
        }
        page = null;
        pageNumber++;
        position = 0;
    }

    public long getMessageCount()
    {
        return messageCount.get();
    }
}