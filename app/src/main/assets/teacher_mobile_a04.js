/* MADRESEYAR A04 — Android-only safe, idempotent enhancement.
 * No business-data edits, no event interception, no server-side modifications.
 * Can be re-evaluated after SPA tab changes without creating another observer.
 */
(function(){
  'use strict';
  try {
    var s=JSON.parse(sessionStorage.getItem('madreseyar.session.v14')||'null');
    if(!s||!s.authUser||s.authUser.role!=='teacher'||!sessionStorage.getItem('madreseyar.staff.token.v14'))return 'not-teacher';
    var app=document.getElementById('appView');
    if(!app||app.classList.contains('hidden'))return 'not-ready';
    document.documentElement.classList.add('msy-a04-native');
    document.documentElement.classList.remove('msy-a03-native');
    var view=document.getElementById('page');
    if(!view)return 'no-page';
    var routeNames={
      myTeaching:['کلاس‌ها و کتاب‌های من','🏫','مدیریت کلاس‌ها و درس‌های اختصاص‌یافته'],
      classroom:['حضور و غیاب','✓','ثبت حضور، فعالیت روزانه و پیگیری کلاس'],
      gradeCenterT07:['دفتر نمره و کارنامه','★','ثبت و مشاهده نمرات و گزارش‌ها'],
      continuousReport:['دفتر نمره و کارنامه','★','ارزیابی مستمر و کارنامه‌های آموزشی'],
      students:['دانش‌آموزان من','♧','فهرست و پرونده دانش‌آموزان مجاز'],
      homeworkCenterV65:['مرکز تکالیف','▤','ساخت، تحویل و بررسی تکالیف'],
      weeklyScheduleV73:['برنامه هفتگی','▦','برنامه کلاس‌ها و تدریس'],
      classJournal:['دفتر روزانه','☷','ثبت رویدادها و فعالیت‌های کلاس'],
      curriculum:['درس‌ها و کتاب‌ها','▣','محتوای آموزشی و منابع درسی'],
      bank:['بانک سؤال','❖','ساخت و مدیریت سؤال‌ها'],
      create:['ساخت آزمون','✎','آماده‌سازی آزمون‌های کلاس'],
      exams:['آزمون‌های من','◈','ویرایش، مدیریت و انتشار آزمون'],
      grading:['تصحیح و نمره‌دهی','☑','بررسی پاسخ‌ها و ثبت نتیجه'],
      appeals:['اعتراض‌های نمره','⚖','پیگیری درخواست‌های بازبینی'],
      quality:['کیفیت سؤال','◇','پایش کیفیت بانک سؤال'],
      reports:['گزارش‌های آموزشی','▥','گزارش‌ها، خروجی‌ها و چاپ'],
      analytics:['تحلیل آموزشی','◉','روندهای تحصیلی و آموزشی'],
      support:['ارتباط و پیگیری','♧','پیام‌ها و ارتباط‌های مجاز'],
      termExamT06:['آزمون پایانی','◈','مدیریت و ثبت نمره پایانی'],
      examprint:['چاپ آزمون','▣','تهیه نسخه چاپی آزمون'],
      workflowHubV90:['چرخه آزمون','⇄','پیگیری مراحل اجرای آزمون'],
      examOperationsHubV80:['عملیات آزمون','◎','بررسی فرایندهای اجرایی آزمون'],
      resultReleaseV84:['انتشار نتایج','↥','کنترل انتشار نمره و نتیجه'],
      appealQueueV88:['صف اعتراض‌ها','☷','مدیریت اعتراض‌های در انتظار'],
      gradingWorkspaceV81:['فضای تصحیح','☑','بررسی و تصحیح پاسخ‌ها'],
      gradeRegisterT07:['ثبت نمرات','★','ثبت و اصلاح مجاز نمرات'],
      gradebookV75:['دفتر نمره','★','دفتر نمره و ارزیابی'],
      stage09Daily:['فعالیت روزانه','☷','ثبت فعالیت‌های روزانه کلاس'],
      assessment:['ارزیابی تحصیلی','▥','ارزیابی و گزارش‌های کلاسی'],
      examActionInboxV79:['اقدام‌های آزمون','◈','اقدام‌های آموزشی در انتظار'],
      examStudentDrilldownV77:['جزئیات آزمون دانش‌آموز','♙','نتایج تفصیلی آزمون'],
      bulkResultReleaseV85:['انتشار گروهی نتایج','↥','انتشار کنترل‌شده نتایج'],
      classReleaseReadinessV86:['آمادگی انتشار','✓','کنترل آمادگی کلاس'],
      appealClosureAuditV89:['بررسی اعتراض‌ها','⚖','گزارش بستن پرونده‌ها'],
      workflowHealthV92:['سلامت فرایند','◇','وضعیت عملیات آموزشی']
    };
    function currentRoute(){
      var t=document.querySelector('#tabs [data-tab].active');
      if(t)return t.getAttribute('data-tab')||'';
      var h=document.querySelector('[data-tab][aria-selected="true"]');
      return h?h.getAttribute('data-tab')||'':'';
    }
    function infer(){
      var route=currentRoute();
      if(route&&routeNames[route])return routeNames[route];
      var headline=view.querySelector('h1,h2');
      var label=headline&&headline.textContent?headline.textContent.trim().slice(0,58):'ابزارهای معلم';
      return [label||'ابزارهای معلم','▤','عملیات مجاز معلم در مدرسه‌یار'];
    }
    function wrapTables(){
      // Do not move live data grids on every render. Moving once preserves
      // input values and event handlers; editable rows remain horizontally scrollable.
      var tables=view.querySelectorAll('table');
      for(var i=0;i<tables.length;i++){
        var table=tables[i], parent=table.parentElement;
        if(!parent||parent.classList.contains('msy-a04-table-scroll')||
           parent.classList.contains('table-wrap')||parent.classList.contains('table-scroll')||
           (parent.className&&typeof parent.className==='string'&&/table-(wrap|scroll)/.test(parent.className)))continue;
        if(table.closest('.modal-backdrop')||table.closest('[contenteditable="true"]'))continue;
        // Editable sheets keep their original DOM ancestry for legacy handlers.
        if(table.querySelector('input,select,textarea,button,[contenteditable]')){
          table.classList.add('msy-a04-editable-table');continue;
        }
        var wrapper=document.createElement('div');wrapper.className='msy-a04-table-scroll';
        parent.insertBefore(wrapper,table);wrapper.appendChild(table);
      }
    }
    function update(){
      var node=document.getElementById('page');
      if(!node||document.getElementById('appView')?.classList.contains('hidden'))return;
      view=node;
      var info=infer(),header=document.getElementById('msy-a04-section-header');
      if(!header){
        header=document.createElement('div');header.id='msy-a04-section-header';
        header.setAttribute('role','heading');header.setAttribute('aria-level','1');
        header.innerHTML='<span class="msy-a04-mark" aria-hidden="true"></span><div class="msy-a04-headtext"><h1></h1><p></p></div>';
        node.insertBefore(header,node.firstChild);
      }else if(header.parentElement!==node)node.insertBefore(header,node.firstChild);
      var name=info[0],icon=info[1],subtitle=info[2];
      var tt=header.querySelector('h1'),mm=header.querySelector('.msy-a04-mark'),dd=header.querySelector('p');
      if(tt&&tt.textContent!==name)tt.textContent=name;
      if(mm&&mm.textContent!==icon)mm.textContent=icon;
      if(dd&&dd.textContent!==subtitle)dd.textContent=subtitle;
      if(view.dataset.msyA04Route!==currentRoute())view.dataset.msyA04Route=currentRoute()||'other';
      wrapTables();
      // Chat widget remains usable, but is compact and moved to the safe edge.
      // Never hide a widget or change its action handlers.
    }
    if(!window.__MSY_A04_TEACHER_MOBILE__){
      window.__MSY_A04_TEACHER_MOBILE__={version:'A04.0-test',updates:0};
      var pending=false;
      var observer=new MutationObserver(function(){
        if(pending)return;pending=true;
        requestAnimationFrame(function(){pending=false;try{update();window.__MSY_A04_TEACHER_MOBILE__.updates++;}catch(e){/* no disruption to server UI */}});
      });
      observer.observe(app,{childList:true,subtree:true,attributes:false});
      window.__MSY_A04_TEACHER_MOBILE__.destroy=function(){observer.disconnect();};
    }
    update();
    return 'ok';
  }catch(ex){return 'theme-error';}
})();
