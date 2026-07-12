-- Never recreate an expired request hash from a late callback.
-- KEYS: request hash, active zset
-- ARGV: requestId, nextCheckMillis, error
if redis.call('exists', KEYS[1]) == 0 then
    return 0
end
redis.call('hset', KEYS[1], 'status', 'COMPENSATING', 'lastError', ARGV[3])
redis.call('zadd', KEYS[2], tonumber(ARGV[2]), ARGV[1])
return 1
