-- Apply a publisher callback only to its current attempt. A return/NACK wins
-- over a later ACK for the same attempt so an unroutable message stays retryable.
-- KEYS: request hash, active zset
-- ARGV: requestId, attempt, outcome(PUBLISHED/PENDING), nextCheckMillis, error
if redis.call('exists', KEYS[1]) == 0 then
    return 0
end
local current = tonumber(redis.call('hget', KEYS[1], 'attempt') or '-1')
local attempt = tonumber(ARGV[2])
if current ~= attempt then
    return 0
end
local failed = tonumber(redis.call('hget', KEYS[1], 'failedAttempt') or '0')
if ARGV[3] == 'PUBLISHED' and failed == attempt then
    return 0
end
if ARGV[3] == 'PENDING' then
    redis.call('hset', KEYS[1], 'failedAttempt', ARGV[2])
end
redis.call('hset', KEYS[1], 'status', ARGV[3], 'lastError', ARGV[5])
redis.call('zadd', KEYS[2], tonumber(ARGV[4]), ARGV[1])
return 1
