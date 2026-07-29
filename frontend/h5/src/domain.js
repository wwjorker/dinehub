export function seckillPhase(coupon, nowEpochMs, owned = false) {
  if (owned) return 'owned'
  if (Number(coupon.remainingStock) <= 0) return 'soldout'
  if (nowEpochMs < Number(coupon.startTimeEpochMs)) return 'pending'
  if (nowEpochMs >= Number(coupon.endTimeEpochMs)) return 'ended'
  return 'live'
}

export function couponThresholdLabel(minAmount, discountAmount) {
  const discount = Number(discountAmount || 0) / 100
  const minimum = Number(minAmount || 0) / 100
  return minimum <= 0 ? `无门槛减 ${discount}` : `满 ${minimum} 减 ${discount}`
}

export function formatBusinessTime(epochMs) {
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(new Date(Number(epochMs)))
}
