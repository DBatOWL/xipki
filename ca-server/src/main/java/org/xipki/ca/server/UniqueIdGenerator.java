// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.ca.server;

import org.xipki.util.Args;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntBinaryOperator;

/**
 * Unique Identifier Generator.
 *
 * <p>An id consists of
 * <ol>
 *  <li>highest bit is set to 0 to assure positive long.
 *  <li>epoch in ms: 46 bits for 1312 years after the epoch</li>
 *  <li>offset: 10 bits
 *  <li>shard_id: 7 bits
 * </ol>
 *
 * <p>Idea is borrowed from http://instagram-engineering.tumblr.com/post/10853187575/sharding-ids-at-instagram
 * @author Lijun Liao (xipki)
 * @since 2.0.0
 *
 */

public class UniqueIdGenerator {

  private static class OffsetIncrement implements IntBinaryOperator {

    @Override
    public int applyAsInt(int left, int right) {
      return (left >= right) ? 0 : left + 1;
    }

  }

  // maximal 10 bits
  private static final int MAX_OFFSET = 0x3FF;

  private final long epoch; // in milliseconds

  private final int shardId; // 7 bits

  private final AtomicInteger offset = new AtomicInteger(0);

  private final IntBinaryOperator accumulatorFunction;

  public UniqueIdGenerator(long epoch, int shardId) {
    this.epoch = Args.notNegative(epoch, "epoch");
    this.shardId = Args.range(shardId, "shardId", 0, 127);
    this.accumulatorFunction = new OffsetIncrement();
  } // constructor

  public long nextId() {
    long now = Clock.systemUTC().millis();
    long ret = now - epoch;
    ret <<= 10;

    ret += offset.getAndAccumulate(MAX_OFFSET, accumulatorFunction);
    ret <<= 7;

    ret += shardId;
    return ret;
  } // method nextId

}
