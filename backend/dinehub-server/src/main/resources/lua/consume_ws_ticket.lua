-- Atomically read and consume a one-time WebSocket ticket.
local value = redis.call('get', KEYS[1])
if value then
    redis.call('del', KEYS[1])
end
return value
