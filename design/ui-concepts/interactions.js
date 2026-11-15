<script>
const root=document.querySelector('.fmconcept');
root.querySelectorAll('[data-review]').forEach(b=>b.addEventListener('click',()=>{const box=b.parentElement.querySelector('.review');box.classList.toggle('show');b.setAttribute('aria-expanded',String(box.classList.contains('show')))}));
let filter='all';const input=root.querySelector('.search');
function applyFilter(){const q=(input?.value||'').toLowerCase();root.querySelectorAll('[data-name]').forEach(item=>{item.hidden=!(item.dataset.name.toLowerCase().includes(q)&&(filter==='all'||item.dataset[filter]==='1'));item.style.display=item.hidden?'none':''})}
root.querySelectorAll('[data-filter]').forEach(b=>b.addEventListener('click',()=>{filter=b.dataset.filter;root.querySelectorAll('[data-filter]').forEach(c=>{c.classList.toggle('active',c===b);c.setAttribute('aria-pressed',String(c===b))});applyFilter()}));input?.addEventListener('input',applyFilter);
root.querySelector('[data-next]')?.addEventListener('click',()=>{const selected=root.querySelector('input[name="firmware"]:checked');root.querySelector('.chosen').textContent=selected.value==='V15'?'Oryginalny firmware · V15':selected.value;root.querySelector('.chosen').closest('.screen').scrollIntoView({block:'nearest',behavior:'smooth'})});
</script>
