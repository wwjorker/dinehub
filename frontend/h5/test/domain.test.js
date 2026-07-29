import test from 'node:test'
import assert from 'node:assert/strict'
import { couponThresholdLabel, seckillPhase } from '../src/domain.js'

const coupon = {
  remainingStock: 5,
  startTimeEpochMs: 1_000,
  endTimeEpochMs: 2_000
}

test('seckill phase is independent from device timezone', () => {
  assert.equal(seckillPhase(coupon, 999), 'pending')
  assert.equal(seckillPhase(coupon, 1_500), 'live')
  assert.equal(seckillPhase(coupon, 2_000), 'ended')
})

test('owned and sold-out phases override the clock', () => {
  assert.equal(seckillPhase(coupon, 1_500, true), 'owned')
  assert.equal(seckillPhase({ ...coupon, remainingStock: 0 }, 1_500), 'soldout')
})

test('zero threshold is shown as no-threshold coupon', () => {
  assert.equal(couponThresholdLabel(0, 1_000), '无门槛减 10')
  assert.equal(couponThresholdLabel(3_000, 1_000), '满 30 减 10')
})
