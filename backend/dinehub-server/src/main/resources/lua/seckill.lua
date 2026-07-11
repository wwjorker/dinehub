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
-- the set is created lazily by the first grant, so it may miss the TTL that
-- publish put on the stock key; align them to avoid a permanent dirty key
if redis.call('ttl', KEYS[2]) == -1 then
    local stockTtl = redis.call('ttl', KEYS[1])
    if stockTtl > 0 then
        redis.call('expire', KEYS[2], stockTtl)
    end
end
return 0
