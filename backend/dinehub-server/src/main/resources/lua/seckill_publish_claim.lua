-- Claim one due publish attempt. Updating the score first prevents two scanners
-- from publishing the same request concurrently; duplicate broker delivery is
-- still harmless because MySQL arbitrates the terminal state.
-- KEYS: request hash, active zset
-- ARGV: requestId, nowMillis, confirmDeadlineMillis
local score = redis.call('zscore', KEYS[2], ARGV[1])
if not score or tonumber(score) > tonumber(ARGV[2]) then
    return -1
end
if redis.call('exists', KEYS[1]) == 0 then
    redis.call('zrem', KEYS[2], ARGV[1])
    return -1
end
local status = redis.call('hget', KEYS[1], 'status')
if status == 'COMPENSATING' or status == 'COMPENSATED' then
    return -1
end
local attempt = redis.call('hincrby', KEYS[1], 'attempt', 1)
redis.call('hset', KEYS[1], 'status', 'PENDING', 'lastError', '')
redis.call('zadd', KEYS[2], tonumber(ARGV[3]), ARGV[1])
return attempt
