--- 参数1，优惠券id
local voucherId = ARGV[1]
--- 参数2 用户id
local userId = ARGV[2]

--- 库存key
local stockKey = 'seckill:stock:' .. voucherId
--- 订单key
local orderKey = 'seckill:order:' .. voucherId

--- 判断库存是否充足
if tonumber(redis.call('get',stockKey)) <= 0 then
    --- 库存不足，返回1
    return 1
end

--- 判断userId是否存在，存在说明重复下单，返回2
if redis.call('sismember',orderKey,userId) == 1 then
    return 2
end
--- 扣库存
redis.call('incrby',stockKey,-1)
--- 下单（保存用户）
redis.call('sadd',orderKey,userId)