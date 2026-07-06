-- Atomic flash-sale admission: stock check, one-per-user check, deduct, record.
-- KEYS[1] stock counter, KEYS[2] set of userIds that already grabbed
-- ARGV[1] userId
-- returns 0 = granted, 1 = sold out, 2 = duplicate user
local stock = tonumber(redis.call('get', KEYS[1]))
if stock == nil or stock <= 0 then
    return 1
end
if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
    return 2
end
redis.call('decr', KEYS[1])
redis.call('sadd', KEYS[2], ARGV[1])
return 0
