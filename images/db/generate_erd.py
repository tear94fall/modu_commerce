#!/usr/bin/env python3
"""커머스 DB ERD 생성기. README 의 images/db/erd_*.png 를 다시 만든다(Graphviz `dot` 필요).

사용:
  cd images/db
  # 1) dev MySQL 에서 스키마를 뽑는다 (modu_infra data/.env 의 COMMERCE_DB_PASSWORD)
  docker exec -e MYSQL_PWD=... mysql-commerce mysql -uroot -N -e "select json_object('table',table_name,'column',column_name,'type',column_type,'nullable',is_nullable,'key',column_key,'extra',extra,'comment',column_comment) from information_schema.columns where table_schema='commerce' order by table_name, ordinal_position" > commerce-columns.jsonl
  docker exec -e MYSQL_PWD=... mysql-commerce mysql -uroot -N -e "select json_object('table',table_name,'column',column_name,'ref_table',referenced_table_name,'ref_column',referenced_column_name,'name',constraint_name) from information_schema.key_column_usage where table_schema='commerce' and referenced_table_name is not null" > commerce-fks.jsonl
  # 2) 그린다
  python3 generate_erd.py .
  # 3) domain_model.png 는 domain_model.dot 에서: dot -Tpng domain_model.dot -o domain_model.png
"""
import json, html, subprocess, sys
SP = sys.argv[1] if len(sys.argv) > 1 else '.'
cols = [json.loads(l) for l in open(f'{SP}/commerce-columns.jsonl')]
fks = [json.loads(l) for l in open(f'{SP}/commerce-fks.jsonl')]
tables = {}  # 테이블 -> 컬럼 목록
for c in cols: tables.setdefault(c['table'], []).append(c)
fkcols = {(f['table'], f['column']) for f in fks}
# 논리 관계(FK 제약 없이 id 만 저장하는 곳)
logical = [
    ('orders','user_id','commerce_customers','user_id'), ('orders','user_coupon_id','user_coupons','id'),
    ('user_coupons','user_id','commerce_customers','user_id'), ('user_coupons','order_id','orders','id'),
    ('wishlists','user_id','commerce_customers','user_id'), ('cart_items','user_id','commerce_customers','user_id'),
    ('addresses','user_id','commerce_customers','user_id'), ('reviews','user_id','commerce_customers','user_id'),
    ('attendance_checks','user_id','commerce_customers','user_id'),
    ('commerce_customers','tier_code','commerce_tiers','code'), ('commerce_tier_histories','user_id','commerce_customers','user_id'),
    ('commerce_tier_coupons','coupon_id','coupons','id'),
    ('promotion_products','product_id','products','id'), ('promotion_coupons','coupon_id','coupons','id'),
    ('push_devices','user_id','commerce_customers','user_id'), ('push_consents','user_id','commerce_customers','user_id'),
    ('push_inbox_items','campaign_id','push_campaigns','id'), ('push_inbox_items','user_id','commerce_customers','user_id'),
    ('push_campaign_opens','campaign_id','push_campaigns','id'), ('push_campaigns','target_id','products','id'),
    ('promotions','point_rule_code',None,None),
]
logcols = {(t,c) for t,c,_,_ in logical}
groups = {
 '상품·카테고리': ['categories','products','product_images','product_option_groups','product_option_values','product_skus','sku_option_values'],
 '고객·회원 등급': ['commerce_customers','commerce_tiers','commerce_tier_coupons','commerce_tier_histories','commerce_tier_runs'],
 '장바구니·배송지·주문·리뷰·찜': ['cart_items','addresses','orders','order_items','reviews','wishlists'],
 '쿠폰': ['coupons','coupon_scope_targets','user_coupons'],
 '기획전·이벤트': ['promotions','promotion_products','promotion_coupons','attendance_checks'],
 '푸시': ['push_devices','push_consents','push_campaigns','push_campaign_opens','push_inbox_items'],
}
def node(t):
    rows = []
    for c in tables[t]:
        badge = 'PK' if c['key']=='PRI' else ('FK' if (t,c['column']) in fkcols else ('→' if (t,c['column']) in logcols else ''))
        col = f"<b>{html.escape(c['column'])}</b>" if badge=='PK' else html.escape(c['column'])
        typ = html.escape(c['type'])
        nn = '' if c['nullable']=='YES' else ' <font color="#8a8f95">*</font>'
        rows.append(f'<tr><td align="left" width="18"><font color="#e5c07b" point-size="9">{badge or ' '}</font></td><td align="left" port="{c["column"]}">{col}{nn}</td><td align="right"><font color="#9da5b4">{typ}</font></td></tr>')
    return f'''  "{t}" [label=<<table border="0" cellborder="0" cellspacing="0" cellpadding="3" bgcolor="#3c3f41">
<tr><td colspan="3" bgcolor="#45494a" align="center"><b>  {t}  </b></td></tr>
{chr(10).join(rows)}</table>>];'''
names = ['products','customers_tiers','orders','coupons','promotions','push']
where = {t:g for g,ts in groups.items() for t in ts}
def stub(t):
    return f'  "{t}" [label=<<table border="0" cellborder="0" cellspacing="0" cellpadding="4" bgcolor="#2f3335"><tr><td><font color="#9da5b4">{t}</font><br/><font color="#6f7780" point-size="8">{html.escape(where.get(t,""))}</font></td></tr></table>>];'
for i,(g,ts) in enumerate(groups.items()):
    out=['digraph erd {', 'graph [bgcolor="#2b2b2b", fontname="Helvetica", fontcolor="#d4d4d4", rankdir=TB, nodesep=0.5, ranksep=0.7, splines=spline, pad=0.3, dpi=160, label="'+g+'", labelloc=t, fontsize=16];',
         'node [shape=plain, fontname="Helvetica", fontsize=11, fontcolor="#dcdcdc"];', 'edge [color="#a0a0a0", fontname="Helvetica", fontsize=9, fontcolor="#c8c8c8", arrowsize=0.8];']
    ext=set()
    for t in ts: out.append(node(t))
    for f in fks:
        if f['table'] in ts or f['ref_table'] in ts:
            for x in (f['table'], f['ref_table']):
                if x not in ts: ext.add(x)
            out.append(f'  "{f["table"]}":"{f["column"]}" -> "{f["ref_table"]}":"{f["ref_column"]}" [xlabel="{f["column"]}"];')
    for t,c,rt,rc in logical:
        if not rt: continue
        if t in ts:
            for x in (t, rt):
                if x not in ts: ext.add(x)
            out.append(f'  "{t}":"{c}" -> "{rt}":"{rc}" [style=dashed, color="#6f9be0", arrowhead=open, xlabel="{c}"];')
    for x in ext: out.append(stub(x))
    out.append('}')
    name=names[i]; fn=f'{SP}/erd_{name}.dot'; open(fn,'w').write('\n'.join(out))
    subprocess.run(['dot','-Tpng',fn,'-o',f'{SP}/erd_{name}.png'],check=True); import os; os.remove(fn)
    print(i+1, g, len(ts), 'tables +', len(ext), 'ext')
