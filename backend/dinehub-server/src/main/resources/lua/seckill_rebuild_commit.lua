-- Fenced, atomic replacement of the Redis stock and bought-user projection.
-- KEYS: rebuild lock, stock, bought, temporary bought snapshot
-- ARGV: lock token, available stock, projection TTL seconds
if redis.call('get', KEYS[1]) ~= ARGV[1] then
    return 0
end
redis.call('set', KEYS[2], ARGV[2], 'EX', tonumber(ARGV[3]))
redis.call('del', KEYS[3])
if redis.call('exists', KEYS[4]) == 1 then
    redis.call('rename', KEYS[4], KEYS[3])
    redis.call('expire', KEYS[3], tonumber(ARGV[3]))
end
return 1
