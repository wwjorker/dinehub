-- Leave the terminal-transition barrier without creating a negative counter.
-- KEYS: terminal in-flight counter
if redis.call('exists', KEYS[1]) == 0 then
    return 0
end
local count = redis.call('decr', KEYS[1])
if count <= 0 then
    redis.call('del', KEYS[1])
    return 0
end
return count
