-- Renew only the rebuild lease owned by this token.
-- KEYS: rebuild lock
-- ARGV: token, lease milliseconds
if redis.call('get', KEYS[1]) ~= ARGV[1] then
    return 0
end
redis.call('pexpire', KEYS[1], tonumber(ARGV[2]))
return 1
