-- Join the terminal-transition barrier unless a rebuild already owns it.
-- KEYS: rebuild lock, terminal in-flight counter
-- ARGV: counterTtlSeconds
if redis.call('exists', KEYS[1]) == 1 then
    return 0
end
local count = redis.call('incr', KEYS[2])
redis.call('expire', KEYS[2], tonumber(ARGV[1]))
return count
