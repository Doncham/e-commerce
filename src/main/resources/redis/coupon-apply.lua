-- KEYS[1] = reqKey       coupon:{id}:req
-- KEYS[2] = quantityKey  coupon:{id}:quantity

-- ARGV[1] = userId
-- ARGV[2] = score

-- return code
-- 1 = ACCEPTED
-- 2 = DUPLICATE
-- 3 = SOLD_OUT
-- 4 = QUANTITY_NOT_INITIALIZED

local reqKey = KEYS[1]
local quantityKey = KEYS[2]

local userId = ARGV[1]
local score = tonumber(ARGV[2])

local quantity = tonumber(redis.call("GET", quantityKey))

if quantity == nil then
    return 4
end

-- zset 크기와 quantity를 비교해서 수량 제한 구현
local currentCount = redis.call("ZCARD", reqKey)

if currentCount >= quantity then
    return 3
end

-- 이미 신청한 유저면 score 갱신하지 않고 중복 반환
local added = redis.call("ZADD", reqKey, "NX", score, userId)

if added == 0 then
    return 2
end

return 1