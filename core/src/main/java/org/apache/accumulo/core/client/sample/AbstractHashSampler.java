/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.accumulo.core.client.sample;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.sample.impl.DataoutputHasher;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * A base class that can be used to create Samplers based on hashing. This class offers consistent
 * options for configuring the hash function. The subclass decides which parts of the key to hash.
 *
 * <p>
 * This class support two options passed into {@link #init(SamplerConfiguration)}. One option is
 * {@code hasher} which specifies a hashing algorithm. Valid values for this option are {@code md5},
 * {@code sha1}, and {@code murmur3_32}. If you are not sure, then choose {@code murmur3_32}.
 *
 * <p>
 * The second option is {@code modulus} which can have any positive integer as a value.
 *
 * <p>
 * Any data where {@code hash(data) % modulus == 0} will be selected for the sample.
 *
 * @since 1.8.0
 */
public abstract class AbstractHashSampler implements Sampler {

  private HashFunction hashFunction;
  private int modulus;

  private static final String HASHER_PROP_NAME = "hasher";
  private static final String MODULUS_PROP_NAME = "modulus";

  protected static final Set<String> REQUIRED_SAMPLER_OPTIONS =
      Set.of(HASHER_PROP_NAME, MODULUS_PROP_NAME);
  private static final Set<String> VALID_HASHERS = Set.of("murmur3_32", "md5", "sha1");

  /**
   * Subclasses with options should override this method to validate subclass options while also
   * calling {@code super.validateOptions(config)} to validate base class options.
   */
  @Override
  public void validateOptions(Map<String,String> config) {
    // Validate required properties, HASHER_PROP_NAME and MODULUS_PROP_NAME
    // Any additional options are validated in calling subclass
    for (Map.Entry<String,String> entry : config.entrySet()) {
      if (entry.getKey().equals(HASHER_PROP_NAME)) {
        checkArgument(VALID_HASHERS.contains(entry.getValue()), "Unknown value for %s: %s",
            HASHER_PROP_NAME, entry.getValue());
      }
      if (entry.getKey().equals(MODULUS_PROP_NAME)) {
        checkArgument(Integer.parseInt(entry.getValue()) > 0, "Improper Integer value for %s: %s",
            MODULUS_PROP_NAME, entry.getValue());
      }
    }
  }

  /**
   * Subclasses with options should override this method and call {@code super.init(config)}.
   */
  @SuppressFBWarnings(value = "UNSAFE_HASH_EQUALS",
      justification = "these hashes don't protect any secrets, just used for binning")
  @Override
  public void init(SamplerConfiguration config) {
    String hasherOpt = config.getOptions().get(HASHER_PROP_NAME);
    String modulusOpt = config.getOptions().get(MODULUS_PROP_NAME);

    requireNonNull(hasherOpt, HASHER_PROP_NAME + " not specified");
    requireNonNull(modulusOpt, MODULUS_PROP_NAME + " not specified");

    switch (hasherOpt) {
      case "murmur3_32":
        hashFunction = Hashing.murmur3_32_fixed();
        break;
      case "md5":
        @SuppressWarnings("deprecation")
        HashFunction deprecatedMd5 = Hashing.md5();
        hashFunction = deprecatedMd5;
        break;
      case "sha1":
        @SuppressWarnings("deprecation")
        HashFunction deprecatedSha1 = Hashing.sha1();
        hashFunction = deprecatedSha1;
        break;
      default:
        throw new IllegalArgumentException("Unknown " + HASHER_PROP_NAME + ": " + hasherOpt);
    }

    modulus = Integer.parseInt(modulusOpt);
  }

  /**
   * Subclass must override this method and hash some portion of the key.
   *
   * @param hasher Data written to this will be used to compute the hash for the key.
   */
  protected abstract void hash(DataOutput hasher, Key k) throws IOException;

  @Override
  public boolean accept(Key k) {
    Hasher hasher = hashFunction.newHasher();
    try {
      hash(new DataoutputHasher(hasher), k);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return hasher.hash().asInt() % modulus == 0;
  }
}
