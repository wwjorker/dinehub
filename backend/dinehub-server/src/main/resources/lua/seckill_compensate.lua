-- Atomic and idempotent Redis compensation.
-- KEYS: marker, stock, bought users
-- ARGV: userId, markerTtlSeconds, keepBought(0/1)
-- returns 1 when this call performed the refund, 0 when already refunded
local ok = redis.call('set', KEYS[1], '1', 'NX', 'EX', tonumber(ARGV[2]))
if not ok then
    return 0
end
if redis.call('exists', KEYS[2]) == 1 then
    redis.call('incr', KEYS[2])
end
-- If the same user already owns the coupon through another request, restore
-- this request's stock but retain one-per-user protection.
if ARGV[3] == '0' then
    redis.call('srem', KEYS[3], ARGV[1])
end
return 1
