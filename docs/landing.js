'use strict';
(() => {
  // return.js runs first, removes confirmation credentials, and isolates the
  // callback before any catalog, artwork, or local-favorite requests begin.
  if (window.authReturnActive) return;
  const $ = id => document.getElementById(id);
  const fields = 'id title{english romaji} coverImage{extraLarge large color} bannerImage description(asHtml:false) genres episodes status format averageScore season seasonYear';
  const query = `query($page:Int!,$genre:String,$search:String){Page(page:$page,perPage:24){pageInfo{hasNextPage} media(type:ANIME,isAdult:false,genre:$genre,search:$search,sort:TRENDING_DESC){${fields}}}}`;
  let media = [], page = 0, filter = '', search = '', generation = 0, pending, hasNext = true, cooldown = 0, hero, selected, toastTimer;
  const known = new Map();
  const favorites = new Map();
  try {
    const stored = JSON.parse(localStorage.getItem('yoru-web-list-v1') || '[]');
    if (Array.isArray(stored)) stored.slice(0,100).forEach(item => {
      if (item && Number.isSafeInteger(item.id) && item.id > 0 && item.title) favorites.set(item.id,item);
    });
  } catch (_) { /* Storage is optional. No app identity is saved here. */ }
  function el(tag, text, className) { const node=document.createElement(tag); if(text!==undefined)node.textContent=text; if(className)node.className=className;return node; }
  function icon(name) { const svg=document.createElementNS('http://www.w3.org/2000/svg','svg'); const use=document.createElementNS('http://www.w3.org/2000/svg','use'); use.setAttribute('href','#'+name);svg.append(use);svg.setAttribute('aria-hidden','true');return svg; }
  function name(item) { return item.title?.english || item.title?.romaji || 'Anime'; }
  function imageUrl(value) { try {const url=new URL(value);return url.protocol==='https:' && ['s4.anilist.co','s3.anilist.co'].includes(url.hostname)?url.href:'';}catch(_){return '';} }
  function setImage(img, value, title='') { const url=imageUrl(value);img.alt=title;if(!url){img.hidden=true;return;}img.hidden=false;img.src=url;img.referrerPolicy='no-referrer';img.onerror=()=>{img.hidden=true;img.parentElement.classList.add('poster-fallback');}; }
  function description(value) {return String(value||'La ficha de este título todavía no incluye una sinopsis.').replace(/<br\s*\/?\s*>/gi,'\n').replace(/<[^>]*>/g,'').replace(/&amp;/g,'&').replace(/&quot;/g,'"').replace(/&#039;|&apos;/g,"'").replace(/&lt;/g,'<').replace(/&gt;/g,'>');}
  function meta(item) {return [item.format==='MOVIE'?'Película':item.format==='TV'?'Serie':item.format, item.episodes?item.episodes+' episodios':null,item.seasonYear].filter(Boolean).join(' · ');}
  function notify(text){$('toast').textContent=text;$('toast').hidden=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').hidden=true,3500);}
  function savedUI(){
    $('saved-count').textContent=favorites.size;
    document.querySelectorAll('[data-save]').forEach(button=>{const saved=favorites.has(Number(button.dataset.save));button.classList.toggle('saved',saved);button.setAttribute('aria-pressed',String(saved));button.setAttribute('aria-label',(saved?'Quitar de mi lista: ':'Guardar en mi lista: ')+name(known.get(Number(button.dataset.save))||{}));});
    if(selected){$('dialog-save').querySelector('span').textContent=favorites.has(selected.id)?'Quitar de mi lista':'Guardar en mi lista';$('dialog-save').setAttribute('aria-pressed',String(favorites.has(selected.id)));}
  }
  function toggle(item){
    if(favorites.has(item.id)){favorites.delete(item.id);notify('Eliminado de tu lista web.');}
    else {if(favorites.size>=100){notify('Tu lista web admite hasta 100 títulos.');return;}favorites.set(item.id,item);notify('Guardado en tu lista web, en este navegador.');}
    try{localStorage.setItem('yoru-web-list-v1',JSON.stringify([...favorites.values()]));}catch(_){notify('Guardado durante esta visita; el navegador no permite almacenamiento.');}
    if(filter==='saved')render([...favorites.values()]);else savedUI();
  }
  function details(item){
    selected=item;setImage($('dialog-cover'),item.coverImage?.extraLarge||item.coverImage?.large,name(item));$('dialog-title').textContent=name(item);$('dialog-meta').textContent=meta(item);$('dialog-description').textContent=description(item.description);$('dialog-genres').replaceChildren(...(item.genres||[]).slice(0,5).map(genre=>el('span',genre)));savedUI();$('anime-dialog').showModal();
  }
  function card(item){
    known.set(item.id,item);const article=el('article',undefined,'anime-card');article.dataset.id=item.id;const poster=el('button',undefined,'poster-button');poster.type='button';poster.setAttribute('aria-label','Ver detalles: '+name(item));
    const img=el('img');img.loading='lazy';img.decoding='async';setImage(img,item.coverImage?.extraLarge||item.coverImage?.large,name(item));poster.append(img);if(!imageUrl(item.coverImage?.extraLarge||item.coverImage?.large))poster.classList.add('poster-fallback');
    if(item.averageScore)poster.append(el('span','★ '+(item.averageScore/10).toFixed(1),'score'));poster.onclick=()=>details(item);
    const save=el('button',undefined,'save-button');save.type='button';save.dataset.save=item.id;save.append(icon('bookmark'));save.onclick=()=>toggle(item);
    const title=el('button',name(item),'anime-name');title.type='button';title.onclick=()=>details(item);
    const info=el('div',undefined,'anime-meta');info.append(el('span',item.format==='MOVIE'?'PELÍCULA':'ANIME'),document.createTextNode(' · '+(item.status==='NOT_YET_RELEASED'?'Próximamente':item.episodes?item.episodes+' ep.':'En catálogo')+(item.seasonYear?' · '+item.seasonYear:'')));
    article.append(poster,save,title,info);return article;
  }
  function render(items){
    $('anime-grid').replaceChildren(...items.map(card));if(!items.length)$('anime-grid').append(el('p',filter==='saved'?'Guarda un anime con el marcador para empezar tu lista web.':'No encontramos ese título. Prueba otro nombre.','empty-catalog'));
    $('load-more').hidden=filter==='saved'||!hasNext; savedUI();
  }
  function heroUI(item, items){
    hero=item;if(!hero)return;setImage($('hero-art'),hero.bannerImage||hero.coverImage?.extraLarge);setImage($('phone-art'),hero.coverImage?.extraLarge||hero.bannerImage);$('featured-name').textContent=name(hero);
    $('phone-posters').replaceChildren(...items.slice(0,3).map(item=>{const img=el('img');img.loading='lazy';setImage(img,item.coverImage?.large,name(item));return img;}));
  }
  async function load(reset=false){
    if(window.authReturnActive)return;
    if(reset){generation++;pending?.abort();media=[];page=0;hasNext=true;render([]);if(filter!=='saved')$('anime-grid').replaceChildren(el('p','Buscando tu próxima historia…','empty-catalog'));}
    if(filter==='saved'){render([...favorites.values()]);$('catalog-status').textContent='Tu lista web se guarda solo en este navegador; no está sincronizada con la app.';return;}
    if(Date.now()<cooldown){$('catalog-status').textContent='El catálogo ha pedido una pausa. Inténtalo de nuevo dentro de un minuto.';return;}
    const current=generation;const next=page+1;const controller=new AbortController();pending=controller;const timeout=setTimeout(()=>controller.abort(),15000);$('load-more').disabled=true;$('catalog-status').textContent='Actualizando el catálogo…';
    try{
      const response=await fetch('https://graphql.anilist.co',{method:'POST',headers:{'Content-Type':'application/json','Accept':'application/json'},credentials:'omit',referrerPolicy:'no-referrer',signal:controller.signal,body:JSON.stringify({query,variables:{page:next,genre:filter||null,search:search||null}})});
      if(response.status===429){cooldown=Date.now()+60000;throw Error('rate-limit');}if(!response.ok)throw Error('catalog');const data=await response.json();if(data.errors||!data.data?.Page)throw Error('catalog');if(current!==generation||window.authReturnActive)return;
      const batch=data.data.Page.media.filter(item=>item&&Number.isSafeInteger(item.id));if(next===1)media=[];const seen=new Set(media.map(item=>item.id));batch.forEach(item=>{if(!seen.has(item.id)){media.push(item);seen.add(item.id);}});page=next;hasNext=data.data.Page.pageInfo.hasNextPage&&media.length<240;render(media);
      $('catalog-status').textContent=search?`${media.length} resultados para «${search}»`:`${media.length} títulos · actualizado al abrir la web`;
    }catch(error){if(current!==generation)return;$('catalog-status').textContent=Date.now()<cooldown?'El catálogo ha pedido una pausa. Inténtalo de nuevo dentro de un minuto.':media.length?'Selección disponible · no se pudo actualizar ahora.':'No se pudo cargar el catálogo. Puedes volver a intentarlo.';if(!media.length)$('anime-grid').replaceChildren(el('p','El catálogo no responde ahora. Pulsa «Ver más anime» para reintentar.','empty-catalog'));$('load-more').hidden=false;}
    finally{clearTimeout(timeout);if(current===generation)$('load-more').disabled=false;}
  }
  function choose(value){filter=value;search='';$('search-input').value='';document.querySelectorAll('[data-filter]').forEach(button=>{const active=button.dataset.filter===value;button.classList.toggle('selected',active);button.setAttribute('aria-pressed',String(active));});load(true);}
  function openSearch(){$('search-form').hidden=false;$('catalogo').scrollIntoView();$('search-input').focus({preventScroll:true});}
  $('open-search').onclick=openSearch;$('catalog-search').onclick=openSearch;
  $('open-list').onclick=$('mobile-list').onclick=()=>{choose('saved');$('catalogo').scrollIntoView();};
  document.querySelectorAll('[data-filter]').forEach(button=>button.onclick=()=>choose(button.dataset.filter));
  $('search-form').onsubmit=event=>{event.preventDefault();const value=$('search-input').value.trim();if(!value)return;filter='';search=value;document.querySelectorAll('[data-filter]').forEach(button=>{button.classList.remove('selected');button.setAttribute('aria-pressed','false');});load(true);};
  $('clear-search').onclick=()=>{$('search-form').hidden=true;choose('');};$('load-more').onclick=()=>load();
  $('featured-title').onclick=()=>{if(hero)details(hero);};$('close-dialog').onclick=()=>$('anime-dialog').close();$('dialog-download').onclick=()=>$('anime-dialog').close();$('dialog-save').onclick=()=>{if(selected)toggle(selected);};
  $('anime-dialog').onclick=event=>{if(event.target===$('anime-dialog')){const box=event.target.getBoundingClientRect();if(event.clientX<box.left||event.clientX>box.right||event.clientY<box.top||event.clientY>box.bottom)event.target.close();}};
  savedUI();
  // Ship a real last-known selection, then refresh once. Failed requests never
  // claim fresh data or replace an existing selection with invented records.
  fetch('catalog.json',{credentials:'omit',referrerPolicy:'no-referrer'}).then(response=>{if(!response.ok)throw Error('snapshot');return response.json();}).then(seed=>{heroUI(seed.hero,seed.media||[]);if(generation===0){media=seed.media||[];render(media);$('catalog-status').textContent='Última selección disponible · actualizando…';}}).catch(()=>{}).finally(()=>{if(generation===0)load();});
})();
