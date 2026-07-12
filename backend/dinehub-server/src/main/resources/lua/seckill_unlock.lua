-- Compare-and-delete so an expired lock is never released by its old owner.
-- KEYS: lock, ARGV: owner token
if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('del', KEYS[1])
end
return 0
