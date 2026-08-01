/**
 * 平滑滚动兼容处理
 * Safari 不支持 scroll-behavior: smooth 时作为后备
 */
document.querySelectorAll('.nav a[href^="#"]').forEach(anchor => {
    anchor.addEventListener('click', e => {
        e.preventDefault();
        const target = document.querySelector(anchor.getAttribute('href'));
        if (!target) return;
        target.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
});

/**
 * 卡片入场淡入动画（Intersection Observer）
 */
const cards = document.querySelectorAll('.feature-card');
if (cards.length && 'IntersectionObserver' in window) {
    cards.forEach(c => c.style.opacity = '0');

    const observer = new IntersectionObserver(entries => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.style.transition = 'opacity .5s ease, transform .5s ease';
                entry.target.style.opacity = '1';
                observer.unobserve(entry.target);
            }
        });
    }, { threshold: .15 });

    cards.forEach(c => observer.observe(c));
}
