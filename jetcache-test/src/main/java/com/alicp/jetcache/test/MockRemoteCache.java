/**
 * Created on  13-09-22 16:54
 */
package com.alicp.jetcache.test;

import com.alicp.jetcache.*;
import com.alicp.jetcache.embedded.LinkedHashMapCacheBuilder;
import com.alicp.jetcache.external.AbstractExternalCache;
import com.alicp.jetcache.external.ExternalCacheConfig;
import com.alicp.jetcache.support.FastjsonKeyConvertor;
import com.alicp.jetcache.support.JavaValueDecoder;
import com.alicp.jetcache.support.JavaValueEncoder;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class MockRemoteCache<K, V> extends AbstractExternalCache<K, V> {
    private Cache<ByteBuffer, byte[]> cache;
    private ExternalCacheConfig<K, V> config;

    public static class MockRemoteCacheTest extends AbstractCacheTest {
        @Test
        public void Test() throws Exception {
            MockRemoteCacheBuilder b = new MockRemoteCacheBuilder();
            b.setKeyConvertor(FastjsonKeyConvertor.INSTANCE);
            b.setValueDecoder(JavaValueDecoder.INSTANCE);
            b.setValueEncoder(JavaValueEncoder.INSTANCE);
            cache = b.buildCache();
            baseTest();
        }
    }


    public MockRemoteCache(MockRemoteCacheConfig<K, V> config) {
        super(config);
        this.config = config;
        cache = LinkedHashMapCacheBuilder.createLinkedHashMapCacheBuilder()
                .limit(config.getLimit())
                .expireAfterWrite(config.getExpireAfterWriteInMillis(), TimeUnit.MILLISECONDS)
                .buildCache();
    }

    @Override
    public CacheConfig<K, V> config() {
        return config;
    }

    private ByteBuffer genKey(K key) {
        return ByteBuffer.wrap(buildKey(key));
    }

    //-------------------------------


    @Override
    public <T> T unwrap(Class<T> clazz) {
        return cache.unwrap(clazz);
    }

    public CacheValueHolder<V> getHolder(K key) {
        return (CacheValueHolder<V>) cache.unwrap(LinkedHashMap.class).get(genKey(key));
    }

    @Override
    protected CacheGetResult<V> do_GET(K key) {
        if (key == null) {
            return new CacheGetResult<V>(CacheResultCode.FAIL, CacheResult.MSG_ILLEGAL_ARGUMENT, null);
        }
        CacheGetResult r = cache.GET(genKey(key));
        if (r.isSuccess()) {
            V v = (V) config.getValueDecoder().apply((byte[]) r.getValue());
            r = new CacheGetResult(CacheResultCode.SUCCESS, null, v);
        }
        return r;
    }

    @Override
    protected MultiGetResult<K, V> do_GET_ALL(Set<? extends K> keys) {
        if (keys == null) {
            return new MultiGetResult<>(CacheResultCode.FAIL, CacheResult.MSG_ILLEGAL_ARGUMENT, null);
        }
        ArrayList<K> keyList = new ArrayList<>(keys.size());
        ArrayList<ByteBuffer> newKeyList = new ArrayList<>(keys.size());
        keys.stream().forEach((k) -> {
            ByteBuffer newKey = genKey(k);
            keyList.add(k);
            newKeyList.add(newKey);
        });
        MultiGetResult<ByteBuffer, byte[]> result = cache.GET_ALL(new HashSet(newKeyList));
        Map<ByteBuffer, CacheGetResult<byte[]>> resultMap = result.getValues();
        if (resultMap != null) {
            Map<K, CacheGetResult<V>> returnMap = new HashMap<>();
            for (int i = 0; i < keyList.size(); i++) {
                K key = keyList.get(i);
                ByteBuffer newKey = newKeyList.get(i);
                CacheGetResult r = resultMap.get(newKey);
                if (r.getValue() != null) {
                    V v = (V) config.getValueDecoder().apply((byte[]) r.getValue());
                    r = new CacheGetResult(r.getResultCode(), null, v);
                }
                returnMap.put(key, r);
            }
            result = new MultiGetResult<ByteBuffer, byte[]>(result.getResultCode(), null, (Map) returnMap);
        }
        return (MultiGetResult) result;
    }

    @Override
    protected CacheResult do_PUT(K key, V value, long expireAfterWrite, TimeUnit timeUnit) {
        if (key == null) {
            return CacheResult.FAIL_ILLEGAL_ARGUMENT;
        }
        return cache.PUT(genKey(key), config.getValueEncoder().apply(value), expireAfterWrite, timeUnit);
    }

    @Override
    protected CacheResult do_PUT_ALL(Map<? extends K, ? extends V> map, long expireAfterWrite, TimeUnit timeUnit) {
        if (map == null) {
            return CacheResult.FAIL_ILLEGAL_ARGUMENT;
        }
        Map<ByteBuffer, byte[]> newMap = new HashMap<>();
        map.entrySet().forEach((e) -> newMap.put(genKey(e.getKey()), config.getValueEncoder().apply(e.getValue())));
        return cache.PUT_ALL(newMap, expireAfterWrite, timeUnit);
    }

    @Override
    protected CacheResult do_REMOVE(K key) {
        if (key == null) {
            return CacheResult.FAIL_ILLEGAL_ARGUMENT;
        }
        return cache.REMOVE(genKey(key));
    }

    @Override
    protected CacheResult do_REMOVE_ALL(Set<? extends K> keys) {
        if (keys == null) {
            return CacheResult.FAIL_ILLEGAL_ARGUMENT;
        }
        return cache.REMOVE_ALL(keys.stream().map((k) -> genKey(k)).collect(Collectors.toSet()));
    }

    @Override
    protected CacheResult do_PUT_IF_ABSENT(K key, V value, long expireAfterWrite, TimeUnit timeUnit) {
        if (key == null) {
            return CacheResult.FAIL_ILLEGAL_ARGUMENT;
        }
        return cache.PUT_IF_ABSENT(genKey(key), config.getValueEncoder().apply(value), expireAfterWrite, timeUnit);
    }
}
