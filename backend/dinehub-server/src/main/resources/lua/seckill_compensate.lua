-- Atomic, idempotent compensation for a failed seckill persistence:
-- give the slot back to the pool and let the user grab again.
-- KEYS[1] compensation marker (unique per requestId)
-- KEYS[2] stock counter, KEYS[3] set of userIds that already grabbed
-- ARGV[1] userId, ARGV[2] marker TTL seconds
-- returns 1 = compensated, 0 = already compensated for this request
local ok = redis.call('set', KEYS[1], '1', 'NX', 'EX', tonumber(ARGV[2]))
if not ok then
    return 0
end
redis.call('incr', KEYS[2])
redis.call('srem', KEYS[3], ARGV[1])
return 1
