import gsap from 'gsap'

let initialized = false

export function useAppEffects(): void {
  if (initialized) return
  initialized = true

  // 背景光球
  const orbsHTML = `<div class="bg-orbs">
    <div class="orb orb-1"></div><div class="orb orb-2"></div><div class="orb orb-3"></div>
  </div>`
  document.body.insertAdjacentHTML('afterbegin', orbsHTML)

  gsap.to('.orb-1', {
    keyframes: [
      { xPercent: -10, yPercent: -5, scale: 1 },
      { xPercent: 10, yPercent: 5, scale: 1.15 },
      { xPercent: -5, yPercent: 10, scale: 0.9 },
      { xPercent: 5, yPercent: -5, scale: 1.05 },
      { xPercent: -10, yPercent: -5, scale: 1 },
    ],
    duration: 12, repeat: -1, ease: 'sine.inOut',
  })
  gsap.to('.orb-2', {
    keyframes: [
      { xPercent: 5, yPercent: 5, scale: 1 },
      { xPercent: -8, yPercent: -8, scale: 0.85 },
      { xPercent: 3, yPercent: 3, scale: 1.1 },
      { xPercent: -5, yPercent: 5, scale: 0.95 },
      { xPercent: 5, yPercent: 5, scale: 1 },
    ],
    duration: 15, repeat: -1, ease: 'sine.inOut',
  })
  gsap.to('.orb-3', {
    keyframes: [
      { xPercent: 3, yPercent: -5, scale: 1 },
      { xPercent: -6, yPercent: 3, scale: 1.1 },
      { xPercent: 8, yPercent: -8, scale: 0.85 },
      { xPercent: -3, yPercent: 5, scale: 1.05 },
      { xPercent: 3, yPercent: -5, scale: 1 },
    ],
    duration: 10, repeat: -1, ease: 'sine.inOut',
  })

  // 光标拖尾（仅桌面端）
  if (!('ontouchstart' in window)) {
    const trails: { el: HTMLDivElement; x: number; y: number }[] = []
    let mx = 0, my = 0
    for (let i = 0; i < 12; i++) {
      const dot = document.createElement('div')
      dot.className = 'cursor-trail'
      dot.style.opacity = String((1 - i / 12) * 0.5)
      dot.style.transform = `translate(-50%,-50%) scale(${1 - i / 12})`
      document.body.appendChild(dot)
      trails.push({ el: dot, x: 0, y: 0 })
    }
    document.addEventListener('mousemove', (e) => { mx = e.clientX; my = e.clientY })
    ;(function tick() {
      let tx = mx, ty = my
      for (let i = 0; i < trails.length; i++) {
        const t = trails[i]
        t.x += (tx - t.x) * (0.35 - i * 0.02)
        t.y += (ty - t.y) * (0.35 - i * 0.02)
        t.el.style.left = t.x + 'px'
        t.el.style.top = t.y + 'px'
        tx = t.x; ty = t.y
      }
      requestAnimationFrame(tick)
    })()
  }

  // 按钮波纹
  document.addEventListener('click', (e) => {
    const btn = (e.target as HTMLElement).closest('button')
    if (!btn) return
    const ripple = document.createElement('span')
    ripple.className = 'ripple'
    const rect = btn.getBoundingClientRect()
    const size = Math.max(rect.width, rect.height)
    ripple.style.width = ripple.style.height = size + 'px'
    ripple.style.left = e.clientX - rect.left - size / 2 + 'px'
    ripple.style.top = e.clientY - rect.top - size / 2 + 'px'
    btn.appendChild(ripple)
    ripple.addEventListener('animationend', () => ripple.remove())
  })

  // 入场动画
  gsap.fromTo('.header h1', { y: -60, opacity: 0 }, { y: 0, opacity: 1, duration: 1, ease: 'expo.out' })
  gsap.fromTo('.upload-card', { y: 40, opacity: 0 }, { y: 0, opacity: 1, duration: 0.7, delay: 0.3, ease: 'expo.out' })
  gsap.fromTo('.gallery-section h2', { y: 30, opacity: 0 }, { y: 0, opacity: 1, duration: 0.6, delay: 0.5, ease: 'power1.out' })
}
