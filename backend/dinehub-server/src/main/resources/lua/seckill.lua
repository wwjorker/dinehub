-- Atomic admission plus a recoverable delivery ledger.
-- KEYS: stock, bought users, request hash, active request zset
-- ARGV: userId, couponId, requestId, nowMillis, ttlSeconds
-- returns 0 granted, 1 sold out, 2 duplicate user
local stock = tonumber(redis.call('get', KEYS[1]))
if stock == nil or stock <= 0 then
    return 1
end
if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
    return 2
end

redis.call('decr', KEYS[1])
redis.call('sadd', KEYS[2], ARGV[1])
redis.call('hset', KEYS[3],
    'userId', ARGV[1],
    'couponId', ARGV[2],
    'status', 'PENDING',
    'attempt', '0',
    'failedAttempt', '0',
    'lastError', '',
    'createdAt', ARGV[4])
redis.call('zadd', KEYS[4], tonumber(ARGV[4]), ARGV[3])

local ttl = tonumber(ARGV[5])
redis.call('expire', KEYS[2], ttl)
redis.call('expire', KEYS[3], ttl)
redis.call('expire', KEYS[4], ttl)
return 0
