/*
 * Copyright (c) 2002-2018 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j Enterprise Edition. The included source
 * code can be redistributed and/or modified under the terms of the
 * GNU AFFERO GENERAL PUBLIC LICENSE Version 3
 * (http://www.fsf.org/licensing/licenses/agpl-3.0.html) with the
 * Commons Clause, as found in the associated LICENSE.txt file.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * Neo4j object code can be licensed independently from the source
 * under separate terms from the AGPL. Inquiries can be directed to:
 * licensing@neo4j.com
 *
 * More information is also available at:
 * https://neo4j.com/licensing/
 */
package org.neo4j.causalclustering.messaging;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;

import java.io.Flushable;
import java.io.IOException;
import java.util.Objects;
import java.util.Queue;

import org.neo4j.kernel.impl.transaction.log.FlushableChannel;

import static java.lang.Integer.min;

/**
 * Uses provied allocator to create {@link ByteBuf}. The byte bufs will be split if maxChunkSize is reached. The full buffer is then added
 * to the provided output and a new buffer is allocated.
 */
public class BoundedNetworkChannel implements FlushableChannel
{
    private static final int DEFAULT_INIT_CHUNK_SIZE = 512;
    private final ByteBufAllocator allocator;
    private final int maxChunkSize;
    private final int initSize;
    private final Queue<ByteBuf> byteBufs;
    private ByteBuf current;
    private boolean isClosed;

    /**
     * @param allocator used to allocated {@link ByteBuf}
     * @param maxChunkSize when reached the current buffer will be moved to the @param outputQueue and a new {@link ByteBuf} is allocated
     * @param outputQueue full or flushed buffers are added here.
     */
    public BoundedNetworkChannel( ByteBufAllocator allocator, int maxChunkSize, Queue<ByteBuf> outputQueue )
    {
        Objects.requireNonNull( allocator, "allocator cannot be null" );
        Objects.requireNonNull( outputQueue, "outputQueue cannot be null" );
        this.allocator = allocator;
        this.maxChunkSize = maxChunkSize;
        this.initSize = min( DEFAULT_INIT_CHUNK_SIZE, maxChunkSize );
        if ( maxChunkSize < Double.BYTES )
        {
            throw new IllegalArgumentException( "Chunk size must be at least 8. Got " + maxChunkSize );
        }
        this.byteBufs = outputQueue;
    }

    /**
     * @return When called will move the current buffer to the queue.
     * This should always be called when finished writing to the buffer to ensure
     * that the last buffer is moved to the output.
     */
    @Override
    public Flushable prepareForFlush()
    {
        return this::storeCurrent;
    }

    @Override
    public FlushableChannel put( byte value )
    {
        checkState();
        prepareWrite( 1 );
        current.writeByte( value );
        return this;
    }

    @Override
    public FlushableChannel putShort( short value )
    {
        checkState();
        prepareWrite( Short.BYTES );
        current.writeShort( value );
        return this;
    }

    @Override
    public FlushableChannel putInt( int value )
    {
        checkState();
        prepareWrite( Integer.BYTES );
        current.writeInt( value );
        return this;
    }

    @Override
    public FlushableChannel putLong( long value )
    {
        checkState();
        prepareWrite( Long.BYTES );
        current.writeLong( value );
        return this;
    }

    @Override
    public FlushableChannel putFloat( float value )
    {
        checkState();
        prepareWrite( Float.BYTES );
        current.writeFloat( value );
        return this;
    }

    @Override
    public FlushableChannel putDouble( double value )
    {
        checkState();
        prepareWrite( Double.BYTES );
        current.writeDouble( value );
        return this;
    }

    @Override
    public FlushableChannel put( byte[] value, int length )
    {
        checkState();
        int writeIndex = 0;
        int remaining = length;
        while ( remaining != 0 )
        {
            int toWrite = prepareGently( remaining );
            ByteBuf current = getOrCreateCurrent();
            current.writeBytes( value, writeIndex, toWrite );
            writeIndex += toWrite;
            remaining = length - writeIndex;
        }
        return this;
    }

    private int prepareGently( int size )
    {
        if ( getOrCreateCurrent().writerIndex() == maxChunkSize )
        {
            prepareWrite( size );
        }
        return min( maxChunkSize - current.writerIndex(), size );
    }

    private ByteBuf getOrCreateCurrent()
    {
        if ( current == null )
        {
            current = allocateNewBuffer();
        }
        return current;
    }

    private void prepareWrite( int size )
    {
        if ( (getOrCreateCurrent().writerIndex() + size) > maxChunkSize )
        {
            storeCurrent();
        }
        getOrCreateCurrent();
    }

    private void storeCurrent()
    {
        if ( current == null )
        {
            return;
        }
        try
        {
            while ( !byteBufs.offer( current ) )
            {
                Thread.sleep( 10 );
            }
            current = null;
        }
        catch ( InterruptedException e )
        {
            Thread.currentThread().interrupt();
            throw new IllegalStateException( "Unable to flush. Thread interrupted" );
        }
    }

    private void releaseCurrent()
    {
        if ( this.current != null )
        {
            current.release();
        }
    }

    private ByteBuf allocateNewBuffer()
    {
        return allocator.buffer( initSize, maxChunkSize );
    }

    private void checkState()
    {
        if ( isClosed )
        {
            throw new IllegalStateException( "Channel has been closed already" );
        }
    }

    @Override
    public void close() throws IOException
    {
        try
        {
            prepareForFlush().flush();
        }
        finally
        {
            isClosed = true;
            releaseCurrent();
        }
    }

    public boolean closed()
    {
        return isClosed;
    }
}
