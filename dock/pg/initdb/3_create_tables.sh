#!/bin/bash
set -e

export PGPASSWORD=${BB_USER_PASSWORD}
psql -v ON_ERROR_STOP=1 -U $BB_USER_LOGIN -d $BB_DB_NAME<<'EOSQL'  

create table data.coin(
    id serial4 NOT NULL,
    code varchar(8) NOT NULL,
    CONSTRAINT coin_pkey PRIMARY KEY (id),
    CONSTRAINT uk_coin_code UNIQUE (code)
); 

-- + SUI
insert into data.coin(code) values
 ('BTC'),
 ('DOGE'),
 ('SOL'),
 ('TON'),
 ('MNT'),
 ('TRX'),
 ('ADA'),
 ('ETH'),
 ('XRP'),
 ('DOT'),
 ('USDT');

CREATE TABLE "data".symbol (
	id serial4 NOT NULL,
	code varchar(24) NOT NULL,
	id_coin int4 NOT NULL,
	is_enabled bool DEFAULT true NOT NULL,
	is_tradable bool DEFAULT true NOT NULL,
	CONSTRAINT ch_tradable_must_be_enabled CHECK (((is_enabled = true) OR ((is_enabled = false) AND (is_tradable = false)))),
	CONSTRAINT symbol_pkey PRIMARY KEY (id),
	CONSTRAINT uk_symbol_code UNIQUE (code),
	CONSTRAINT fk_symbol_coin FOREIGN KEY (id_coin) REFERENCES "data".coin(id)
);

-- + SUIUSDT
INSERT INTO data.symbol(code,id_coin) 
select ds.symbolCode,c.id
from(
	SELECT symbolCode, coinCode
	FROM unnest(ARRAY['BTCUSDT','DOGEUSDT','SOLUSDT','TONUSDT','MNTUSDT','TRXUSDT','ADAUSDT','ETHUSDT','XRPUSDT','DOTUSDT'], 
	            ARRAY['BTC','DOGE','SOL','TON','MNT','TRX','ADA','ETH','XRP','DOT']
	            ) AS t(symbolCode, coinCode)
) ds
join data.coin c on c.code = ds.coinCode;  

create table data.trade_category(
 id   int primary key,
 code varchar(24) not null,
 CONSTRAINT uk_trade_category_code UNIQUE (code)
);

insert into data.trade_category(id,code) values(1,'spot');

create table data.trade_side(
 id   int primary key,
 code varchar(4) not null,
 CONSTRAINT uk_trade_side_code UNIQUE (code)
);

insert into data.trade_side(id,code) values(1,'Buy');
insert into data.trade_side(id,code) values(2,'Sell');

CREATE TABLE "data".trade_order_type (
	id int4 NOT NULL,
	code varchar(24) NOT NULL,
	CONSTRAINT trade_order_type_pkey PRIMARY KEY (id),
	CONSTRAINT uk_trade_order_type UNIQUE (code)
);

insert into data.trade_order_type(id,code) values(1,'Market');
insert into data.trade_order_type(id,code) values(2,'Limit'); 

CREATE TABLE data.market_unit (
	id int4 NOT NULL,
	code varchar(9) NOT NULL,
	CONSTRAINT market_unit_pkey PRIMARY KEY (id),
	CONSTRAINT uk_market_unit_code UNIQUE (code)
);

insert into data.market_unit(id,code) values(1,'baseCoin');
insert into data.market_unit(id,code) values(2,'quoteCoin');

CREATE OR REPLACE VIEW "data".v_symbols
AS SELECT s.id,
    s.code,
    c.code AS coin,
    s.is_enabled,
    s.is_tradable
   FROM data.symbol s
     JOIN data.coin c ON c.id = s.id_coin;

CREATE TABLE data.trade_advice (
	id             bigserial NOT null primary key,
	ts_db          timestamp DEFAULT LOCALTIMESTAMP NOT NULL,
	id_symbol      int4 NOT null,
	id_category    int not null constraint fk_trade_advice_categ references data.trade_category(id),  
	id_side        int not null constraint fk_trade_advice_side  references data.trade_side(id),
	isLeverage     boolean not null,
	id_order_type  int not null constraint fk_trade_advice_ord_typ references data.trade_order_type(id),
	qty            numeric not null,
	id_market_unit int not null constraint fk_trade_advice_mrk_unit references data.market_unit(id),
	slippageToleranceType varchar(24) default 'Percent',
	slippageTolerance     numeric default 0.1,
	is_taken              boolean not null default false,
	taken_ts              timestamp,
	who_insert            varchar(100),
	to_close_orderid      int8
);

--TODO: check that index is using on big test dataset
create index idx_trade_advice_ts_db_desc on data.trade_advice(id_symbol,ts_db desc) where is_taken = false;

create table data.trade_advice_order(
  orderId         int8 primary key,
  id_trade_advice int8 not null constraint fk_trade_advice_order_ta references data.trade_advice(id),
  ts_db           timestamp DEFAULT LOCALTIMESTAMP not null,
  ts_bybit        int8 not null
);

comment on column data.trade_advice_order.id_trade_advice is 'link to trade advice data.trade_advice (advice by algo)';
comment on column data.trade_advice_order.ts_bybit is 'ts when order was created, returned from /v5/order/create with orderId'; 

CREATE TABLE data.bb_order (
	orderid int8 NULL,
	orderlinkid int8 NULL,
	symbol varchar(24) NULL,
	price numeric NULL,
	qty numeric NULL,
	side varchar(10) NULL,
	isleverage int4 NULL,
	orderstatus varchar(100) NULL,
	canceltype varchar(100) NULL,
	rejectreason varchar(100) NULL,
	avgprice numeric NULL,
	cumexecfee numeric NULL,
	slippagetolerancetype varchar(24) NULL,
	marketunit varchar(15) NULL,
	slippagetolerance numeric NULL
);

comment on table data.bb_order is 'Just information about opened order, returned from /v5/order/history';
 
CREATE OR REPLACE VIEW data.v_trade_advice
AS SELECT 
    ta.id AS trade_advice_id,
    ta.id_symbol,
    tc.code AS category,
    s.code AS symbol,
    ta.isleverage::integer AS isleverage,  
    ts.code AS side,
    tot.code AS ordertype,
    ta.qty,
    mu.code AS marketunit,
    ta.slippagetolerancetype,
    ta.slippagetolerance
   FROM data.trade_advice ta
     JOIN data.symbol s ON s.id = ta.id_symbol
     JOIN data.trade_category tc ON tc.id = ta.id_category
     JOIN data.trade_side ts ON ts.id = ta.id_side
     JOIN data.trade_order_type tot ON tot.id = ta.id_order_type
     JOIN data.market_unit mu ON mu.id = ta.id_market_unit
  WHERE ta.is_taken = false
  ORDER BY ta.ts_db DESC
 LIMIT 1;
   
create table data.order_book_snapshot(
 id        bigserial PRIMARY KEY,
 id_symbol int not null constraint fk_ob_snap_symbol references data.symbol(id) on delete cascade,
 ts_db     timestamp DEFAULT LOCALTIMESTAMP NOT NULL,
 ts_bybit  int8 not null,
 constraint uk_order_book_snapshot unique(id_symbol,ts_db)
);

CREATE INDEX idx_desc_obs_ts_bybit         ON data.order_book_snapshot USING btree (ts_bybit DESC);
create index idx_order_book_ss_symbol      on data.order_book_snapshot(id_symbol);
create index idx_order_book_ss_symbol_tsbb on data.order_book_snapshot(id_symbol,ts_bybit);

create table data.order_book(
id          bigserial,
id_snapshot bigint not null constraint fk_ob_snap references data.order_book_snapshot(id) on delete cascade,
side        varchar(1) not null,
price       numeric,
amount      numeric,
constraint ch_side check (side in ('a','b')) 
);
   
create index idx_ob_id_snap on data.order_book(id_snapshot);
create index idx_ob_id_snap_side_price on data.order_book(id_snapshot,side,price desc);

create table data.open_interest(
  id        bigserial PRIMARY KEY,
  id_symbol int not null constraint fk_oi_symbol references data.symbol(id) on delete cascade,
  ts_db     timestamp DEFAULT LOCALTIMESTAMP NOT NULL,
  ts_bybit  int8 not null,     
  oi        numeric,
  constraint uk_open_interest unique(id_symbol,ts_db)
); 

create index idx_io_symbol on data.open_interest(id_symbol,ts_bybit);

-- "confirm":true
create table data.candle(
 id         bigserial primary key,
 id_symbol  int not null constraint fk_candle_symbol references data.symbol(id) on delete cascade,
 ts_db      timestamp DEFAULT LOCALTIMESTAMP NOT NULL,
 start_ts   bigint,
 end_ts     bigint,
 c_interval varchar(3) not null,
 o          numeric,
 h          numeric,
 l          numeric,
 c          numeric, 
 v          numeric,
 constraint uk_candle unique(id_symbol,start_ts,end_ts) 
);

CREATE INDEX idx_start_ts_desc_candle ON data.candle USING btree (start_ts desc) WHERE (start_ts IS not NULL);

-- create index idx_candle_symbol_interval_start on data.candle(id_symbol,c_interval,start_ts);
create unique index uk_cancle_single_open_candle on data.candle(id_symbol,c_interval) where start_ts is null;  

CREATE INDEX idx_candle_symbol_int_1m_tsdb ON data.candle USING btree (id_symbol, ts_db) WHERE ((c_interval)::text = '1'::text);
CREATE INDEX idx_candle_symbol_int_5m_tsdb ON data.candle USING btree (id_symbol, ts_db) WHERE ((c_interval)::text = '5'::text);
CREATE INDEX idx_candle_symbol_int_15m_tsdb ON data.candle USING btree (id_symbol, ts_db) WHERE ((c_interval)::text = '15'::text);
CREATE INDEX idx_candle_symbol_int_60m_tsdb ON data.candle USING btree (id_symbol, ts_db) WHERE ((c_interval)::text = '60'::text);

CREATE INDEX idx_candle_symbol_int_15m_sts ON data.candle USING btree (id_symbol, start_ts) WHERE ((c_interval)::text = '15'::text);
CREATE INDEX idx_candle_symbol_int_1m_sts ON data.candle USING btree (id_symbol, start_ts) WHERE ((c_interval)::text = '1'::text);
CREATE INDEX idx_candle_symbol_int_5m_sts ON data.candle USING btree (id_symbol, start_ts) WHERE ((c_interval)::text = '5'::text);
CREATE INDEX idx_candle_symbol_int_60m_sts ON data.candle USING btree (id_symbol, start_ts) WHERE ((c_interval)::text = '60'::text); 

-- internal data, "confirm":false 
create table data.kline(
 id_candle bigint not null constraint fk_kline_candle references data.candle(id) on delete cascade, 
 ts_db      timestamp DEFAULT LOCALTIMESTAMP NOT NULL,
 start_ts   bigint not null,
 end_ts     bigint not null,
 o          numeric not null,
 h          numeric not null,
 l          numeric not null,
 c          numeric not null,
 v          numeric not null
);

-- create index idx_kline_candle on data.kline(id_candle, start_ts);
CREATE INDEX idx_kline_candle_tsdb ON data.kline USING btree (id_candle, ts_db);

CREATE TABLE "data"."interval" (
	id serial4 NOT NULL,
	code varchar(3) NOT NULL,
	mins int4 NULL,
	CONSTRAINT interval_pkey PRIMARY KEY (id),
	CONSTRAINT uk_interval_code UNIQUE (code)
);

insert into data.interval (code,mins)
select code,mins
  from unnest(ARRAY['1','3','5','15','30','60','120','240','360','720','D','W','M']::varchar[],
              ARRAY[1,3,5,15,30,60,120,240,360,720,null,null,null]::int[]) as t(code,mins); 

create table data.ref_symbol_interval(
  id_symbol   int4 not null constraint fk_ref_symb_interv_symbol   references data.symbol(id), 
  id_interval int4 not null constraint fk_ref_symb_interv_interval references data.interval(id),
  constraint uk_ref_symbol_interval unique(id_symbol,id_interval)
);
comment on table data.ref_symbol_interval is 'Symbols and interval pairs for saving candles/klines';

insert into data.ref_symbol_interval(id_symbol,id_interval)
select s.id,i.id 
  from data.symbol s
 cross join data.interval i      
 where s.is_enabled = true and 
       i.code in ('1','5','15','30','60')
 order by 1,2;      

-- for /v5/account/wallet-balance (common)
create table data.wallet_balance(
 id                    bigserial primary key,
 ts_db                 timestamp DEFAULT LOCALTIMESTAMP NOT NULL,
 totalEquity           numeric not null,
 totalInitialMargin    numeric not null,
 totalAvailableBalance numeric not null,
 totalWalletBalance    numeric not null,
 ts_bybit              int8 NOT NULL
);

create index idx_desc_wb_ts_bybit on data.wallet_balance(ts_bybit desc);

-- for /v5/account/wallet-balance (by coins)
create table data.wallet_balance_coin(
 id                    bigserial primary key,
 ts_db                 timestamp DEFAULT LOCALTIMESTAMP NOT NULL,
 id_wallet_balance     int8 not null constraint fk_wallet_blnc_coin_wb   references data.wallet_balance(id) on delete cascade,
 id_coin               int4 not null constraint fk_wallet_blnc_coin_coin references data.coin(id),
 equity                numeric not null,
 usdValue              numeric not null,
 cumRealisedPnl        numeric not null
);

create index idx_wallet_balance_coin_wb on "data".wallet_balance_coin(id_wallet_balance);

CREATE OR REPLACE VIEW "data".ref_symbols_intervals
AS
SELECT ref_sy.id_symbol,
    ref_sy.id_interval,
    (('kline.'::text || i.code::text) || '.'::text) || s.code::text AS kline_topic,
    i.code as c_interval
   FROM data.ref_symbol_interval ref_sy
     JOIN data.symbol s ON s.id = ref_sy.id_symbol
     JOIN data."interval" i ON i.id = ref_sy.id_interval;

create or replace view data.symbols_ob_stat as
select s.code , 
       count(distinct ob.id_snapshot) as ss_count,
       count(ob.id) as cnt 
from data.symbol s 
left join data.order_book_snapshot obs on obs.id_symbol  = s.id
left join data.order_book ob on obs.id = ob.id_snapshot
group by s.code; 

create table data.trade_meta(
 id_symbol   int not null constraint fk_trade_meta_symbol references data.symbol(id),
 tpsl_diff   numeric not null,
 qty         numeric not null,
 isleverage  int4 default 0
);

insert into data.trade_meta(id_symbol,tpsl_diff,qty,isleverage) values(5,0.0070,25.0,0);


create table data.bb_log(
 id           bigserial primary key,
 ts_db        timestamp DEFAULT LOCALTIMESTAMP NOT NULL,
 bb_module    varchar(256) not null,
 bb_action    varchar(256) not null,
 error_class  varchar(256) not null,
 msg          varchar(1000)
);

create table data.log_level(
 id   int primary key,
 code varchar(5) not null,
 constraint ul_log_level_code unique(code)
);

insert into data.log_level(id,code) values(1,'info'); 
insert into data.log_level(id,code) values(2,'trace'); 
insert into data.log_level(id,code) values(3,'error'); 
insert into data.log_level(id,code) values(4,'debug'); 
insert into data.log_level(id,code) values(5,'fatal'); 
insert into data.log_level(id,code) values(6,'trade');  

create table data.common_log( 
 id                bigserial primary key,
 id_log_level      int4 not null constraint fk_common_log_level  references data.log_level(id),
 id_symbol         int4  constraint fk_common_log_symbol references data.symbol(id),
 ts_db             timestamp DEFAULT LOCALTIMESTAMP NOT NULL,
 module            varchar(512) not null, 
 action            varchar(512) not null,
 error_class       varchar(512) not null,
 msg               varchar(1000),
 orderid_to_close  int8
);

create index idx_common_log_sym_mod_info on data.common_log(module,id_symbol,ts_db desc) where id_log_level=1 and id_symbol is not null;
create index idx_common_log_sym_mod_trade on data.common_log(module,id_symbol,ts_db desc) where id_log_level=6 and id_symbol is not null;  

create table data.reglament(
 id       serial primary key,
 prop     text not null,
 code     varchar(32) not null,
 int_val  int  not null,
 constraint uk_reglament unique(code)
);

insert into data.reglament(prop,code,int_val) values('Keep wallet_balance days','keep_wb_days',10);
insert into data.reglament(prop,code,int_val) values('Keep orderorder_book_snapshot days','keep_obs_days',10);
insert into data.reglament(prop,code,int_val) values('Keep candle days','keep_candle_days',10);
insert into data.reglament(prop,code,int_val) values('Keep open interset days', 'keep_oi_days', 10);

create table data.reglament_log(
 id           bigserial primary key,
 id_reglament int not null constraint fk_regl_log_regl references data.reglament(id),
 begin_ts     timestamp default localtimestamp not null,
 end_ts       timestamp,
 deleted_rows int
);

create table data.advice_meta(
 id                  serial CONSTRAINT advice_meta_pkey PRIMARY key,
 advice_description  text not null,
 proc                text not null
);
comment on column data.advice_meta.proc is 'Name of procedure for this advices - fixed API (id_symbol, start_ts) :text - buy, sell, null';

insert into data.advice_meta(advice_description,proc) values('We are looking for a bar with high volume, after which the price went to the side of the bar.','bounce_turn');
insert into data.advice_meta(advice_description,proc) values('We observe when the price suddenly breaks out of the channel.','exit_from_channel');

create table data.ref_advice_meta_interval(
 id_advice_meta int4 not null constraint fk_ref_advice_meta_interval_am references data.advice_meta(id),
 id_interval    int4 not null constraint fk_ref_advice_meta_interval_in references data.interval(id),
 constraint uk_ref_advice_meta_interval unique(id_advice_meta,id_interval)
);

insert into  data.ref_advice_meta_interval(id_advice_meta,id_interval) values(1,4);
insert into  data.ref_advice_meta_interval(id_advice_meta,id_interval) values(2,4);

CREATE TABLE data.advice (
	id serial4 NOT NULL,
	adviser_id int4 NOT NULL,
	id_candle int8, -- nullable because reglament cleaning of data.candle
	id_symbol int4 NOT NULL,
	ts_db timestamp NOT NULL,
	start_ts int8 NOT NULL,
	end_ts int8 NOT NULL,
	c_interval varchar(3) NOT NULL,
	o numeric NOT NULL,
	h numeric NOT NULL,
	l numeric NOT NULL,
	c numeric NOT NULL,
	v numeric NOT NULL,
	advice text NOT NULL,
	is_sent_to_user bool DEFAULT false NOT NULL,
	CONSTRAINT advice_pkey PRIMARY KEY (id),
	CONSTRAINT ch_advice_str CHECK ((advice = ANY (ARRAY['buy'::text, 'sell'::text]))),
	CONSTRAINT fk_advice_advice_meta FOREIGN KEY (adviser_id) REFERENCES "data".advice_meta(id),
	CONSTRAINT fk_advice_candle FOREIGN KEY (id_candle) REFERENCES "data".candle(id) on delete set null, 
	CONSTRAINT fk_advice_symbol FOREIGN KEY (id_symbol) REFERENCES "data".symbol(id)
);

-- TODO: add UK adviser_id + id_candle   

create index idx_advice_not_sent on data.advice(is_sent_to_user,id) where is_sent_to_user=false; 

create or replace view data.v_candle_kline as
select c.id_symbol,
       c.c_interval,
       k.id_candle,
       k.ts_db,
       k.start_ts,
       k.o,
       k.h,
       k.l,
       k.c,
       k.v
from  data.candle c
left join data.kline k on k.id_candle = c.id
where c.start_ts  is not null
order by c.id_symbol,c.c_interval,k.id_candle;

create or replace view data.v_curr_state as
select 'wallet_balance' as metric,
       wb.ts_db         as last_date_time,
       (round(EXTRACT(EPOCH FROM (current_timestamp-to_timestamp(wb.ts_bybit::double precision/1000.0)))) < 10) as is_actual
from   data.wallet_balance wb 
where  wb.id = (select max(wbi.id) from data.wallet_balance wbi)
union all
  select 'candle_kline_'||s.code as metric,
         null::timestamp as last_date_time,
         (case 
           when sum(1) = sum(case when is_actual then 1 else 0 end) 
           then true 
           else false
          end) as is_actual
  from(
	 select ds.*,
	        round(EXTRACT(EPOCH FROM (current_timestamp-to_timestamp(ds.max_start_ts::double precision/1000.0)))) as diff_seconds,
	        (case
	          when round(EXTRACT(EPOCH FROM (current_timestamp-to_timestamp(ds.max_start_ts::double precision/1000.0)))) <= c_interval::int * 60 
	          then true 
	          else false
	        end) as is_actual
	  from(
			select ci.id_symbol,
			       ci.c_interval,
			       max(k.start_ts) as max_start_ts
			  from data.candle ci
			  join data.kline k on k.id_candle =ci.id
			 where ci.start_ts is null
			 group by ci.id_symbol, ci.c_interval
		) ds
 ) dse
 join data.symbol s on s.id = dse.id_symbol
 group by s.code
 order by 1 desc; 


-- select * from data.view_deep(p_interval => '15', p_deep_bars => 10);

CREATE OR REPLACE FUNCTION data.view_deep(p_interval text, p_deep_bars int)
 RETURNS TABLE(
				code varchar(24),   
				p_first numeric,
				c_last numeric,
				dif_prcnt numeric,
				move_dir varchar(4),
				smpl_volat numeric
              )
 LANGUAGE sql
AS $function$
with ds as (
			select c.*,
			       (case when row_number() over(partition by c.id_symbol order by c.ts_db asc )=1 then 1 else 0 end) as is_first,
			       (case when row_number() over(partition by c.id_symbol order by c.ts_db desc)=1 then 1 else 0 end) as is_last
			from   data.candle c,
				   (
					select ci.id_symbol, 
					       max(ci.ts_db) - make_interval(mins => p_interval::int * p_deep_bars) as ts_from,
					       max(ci.ts_db) as ts_to
					from   data.candle ci
					where  ci.start_ts is not null and 
					       ci.c_interval = p_interval
					group by ci.id_symbol
				   ) intrvls
			where  c.start_ts is not null and 
			       c.c_interval = p_interval and 
			       c.id_symbol = intrvls.id_symbol and 
			       c.ts_db between intrvls.ts_from and intrvls.ts_to 
           ),
    dsvlt as (
				 select c.id_symbol,
				        --min(c.l) as min_l,
				        --round(avg((c.o+c.c)/2),4) as avg_p,
				        --max(c.h) as max_h,
				        round(((max(c.h) - min(c.l))/round(avg((c.o+c.c)/2),4))*100,2) as smpl_volat
				   from ds c
				  group by c.id_symbol  
             ),       
    diff as (
			select cf.id_symbol,
			       cf.o as p_first,
			       cl.c as c_last,
			       round(((cl.c - cf.o)/cf.o)*100,2) as dif_prcnt,
			       (case when cf.o < cl.c then 'up' else 'down' end) as move_dir
			from ds cf, 
			     ds cl
			where cf.id_symbol = cl.id_symbol and
			      cf.is_first = 1 and 
			      cl.is_last  = 1
             )
    select null::varchar(24) as code,
           null::numeric     as p_first,
           null::numeric     as c_last,
           null::numeric     as dif_prcnt,
           (case
             when sum(1) = sum(case when df.move_dir ='up'   then 1 else 0 end) then 'UP'
             when sum(1) = sum(case when df.move_dir ='down' then 1 else 0 end) then 'DOWN'
             else 'DIFF'
            end) as move_dir,
           null::numeric     as smpl_volat
      from diff df
union all    
    select s.code,
           --df.id_symbol,
           df.p_first,
           df.c_last,
           df.dif_prcnt,
           df.move_dir,
           vt.smpl_volat
      from diff df,
           dsvlt vt,
           data.symbol s 
     where df.id_symbol = vt.id_symbol and 
           df.id_symbol = s.id
order by smpl_volat desc nulls first
$function$
;


/*
create table data.test_advise(
	p_id_symbol int4, 
	p_start_ts  int8,
	p_advise    text
);

delete from data.test_advise;

insert into data.test_advise
     select c.id_symbol , c.start_ts ,data.test_advise(c.id_symbol, c.start_ts) as p_advise
	   from data.candle c 
	  where c.start_ts is not null and 
	        c.c_interval = '15' 
    order by c.id_symbol, c.start_ts

    select a.p_id_symbol, sum(1) as cnt
    from   data.test_advise a 
    where  a.p_advise is not null
    group by a.p_id_symbol
    order by 1
    
*/  

CREATE OR REPLACE FUNCTION data.call_advice_function(func_name text,id_symbol int,ts bigint) RETURNS text AS $$
DECLARE
  result text;
BEGIN
  EXECUTE format('SELECT data.%I(%L, %L)', func_name, id_symbol, ts) INTO result;
  RETURN result;
END;
$$ LANGUAGE plpgsql STRICT;


CREATE OR REPLACE FUNCTION data.bounce_turn(p_id_symbol int4, p_start_ts int8)
 RETURNS text
 LANGUAGE sql
AS $function$
with dataset_c as (
		             select cii.* 
		               from data.candle cii 
		              where cii.start_ts is not null and 
		                    cii.c_interval = '15' and 
                            cii.id_symbol  = p_id_symbol and 
		                    cii.start_ts  <= p_start_ts 
                  ),
   ds_filtered_maxv as (
					     select c.*,
					            (case when c.v = max(c.v) over() then c.start_ts else null::int end)               as max_volume_bar,
					            max(c.v) over()                                                                    as max_vol,
					            (case when c.v = max(c.v) over() then c.c else null::int end)                      as max_volume_close,
					            (case when c.start_ts = min(c.start_ts) over() then c.start_ts else null::int end) as fisrt_start_ts, 
					            (case when c.start_ts = max(c.start_ts) over() then c.start_ts else null::int end) as last_start_ts,
					            (case when c.start_ts = max(c.start_ts) over() then c.c else null::int end)        as last_close
					       from dataset_c c 
						  where c.c_interval = '15' and 
						        c.start_ts > (select max(cm.start_ts) from dataset_c cm) - 33400000
	                   ),-- select * from ds_filtered_maxv, 
	ds_common_filter as (               
						  select sum(1) as cnt,
						         sum(max_volume_bar)                        as max_volume_bar,
						         max(max_vol)                               as max_vol,
						         max(max_volume_close)                      as max_volume_close,
						         sum(fisrt_start_ts)                        as fisrt_start_ts,
						         sum(last_start_ts)                         as last_start_ts,
						         sum(last_start_ts)  - sum(fisrt_start_ts)  as first_last_interval,
						         sum(max_volume_bar) - sum(fisrt_start_ts)  as first_maxvol_interval,
						         sum(last_start_ts)  - sum(max_volume_bar)  as maxvol_last_interval,
						         max(last_close)                            as last_close
						    from ds_filtered_maxv c 
						   where c.max_volume_bar is not null or 
						         c.fisrt_start_ts is not null or
						         c.last_start_ts  is not null
						  having sum(1)=3 --must be equal 3, one bar with max volume     
						     and (sum(last_start_ts)  - sum(fisrt_start_ts)) = 33300000
						     and (sum(max_volume_bar) - sum(fisrt_start_ts)) > (sum(last_start_ts)  - sum(max_volume_bar))*3
	                    ),-- select * from ds_common_filter
	ds_maxvol_bar as (                    
					  select (case when c.o < c.c then 'up'
					               when c.o > c.c then 'down'
					            else 'unknown' end) as bar_type,
					         c.c as maxvol_bar_close
					    from ds_filtered_maxv c
					   where exists(select * from ds_common_filter) and 
					         c.max_volume_bar is not null
	                 ),
	ds_first_maxval as (                 
					      select avg(c.v)                    as first_maxvol_avg_volume,
					             round(avg((c.c + c.o)/2),3) as first_maxvol_avg_price 
						    from ds_filtered_maxv c
						   where exists(select * from ds_common_filter) and 
						         c.start_ts >= (select cf.fisrt_start_ts from ds_filtered_maxv cf where  cf.fisrt_start_ts is not null) and 
						         c.start_ts <  (select cf.max_volume_bar from ds_filtered_maxv cf where  cf.max_volume_bar is not null)
                       ),
   ds_maxval_last as (
				      select avg(c.v)                    as first_maxvol_avg_volume,
				             round(avg((c.c + c.o)/2),3) as first_maxvol_avg_price 
					    from ds_filtered_maxv c
					   where exists(select * from ds_common_filter) and 
					         c.start_ts >  (select cf.max_volume_bar from ds_filtered_maxv cf where  cf.max_volume_bar is not null) and 
					         c.start_ts <= (select cf.last_start_ts  from ds_filtered_maxv cf where  cf.last_start_ts is not null)
	                 )
      select --4 as id_symbol,
             --fnl.last_start_ts as start_ts,
             (case
      	        when mvb.bar_type ='up'   then 'buy'
	            when mvb.bar_type ='down' then 'sell'
              end) as order_advise
             --fnl.*, '************' as div1, mvb.*,'************' as div2, fmv.*,'************' as div3, mvl.*
	    from 
	         ds_common_filter fnl,
	         ds_maxvol_bar    mvb,
	         ds_first_maxval  fmv,
	         ds_maxval_last   mvl,
	         (
				select  1 as is_symbol, 5.5 as vol_koeff union all 
				select  2 as is_symbol, 6.5 as vol_koeff union all 
				select  3 as is_symbol, 5.5 as vol_koeff union all 
				select  4 as is_symbol, 4   as vol_koeff union all 
				select  5 as is_symbol, 4   as vol_koeff union all 
				select  6 as is_symbol, 2.5 as vol_koeff union all 
				select  7 as is_symbol, 8   as vol_koeff union all 
				select  8 as is_symbol, 2.5 as vol_koeff union all 
				select  9 as is_symbol, 4   as vol_koeff union all 
				select 10 as is_symbol, 6   as vol_koeff 
	         ) as volume_koeff
	   where 
	         (
	           (fnl.last_close > mvb.maxvol_bar_close and mvb.bar_type ='up' and fnl.max_volume_close < fmv.first_maxvol_avg_price ) or 
	           (fnl.last_close < mvb.maxvol_bar_close and mvb.bar_type ='down' and fnl.max_volume_close > fmv.first_maxvol_avg_price )
	         ) -- last price compare to maxvol price
         and fnl.max_vol > fmv.first_maxvol_avg_volume * volume_koeff.vol_koeff  -- max volume gt. avg in prev interval
         and fnl.max_vol > mvl.first_maxvol_avg_volume
         and mvb.bar_type  in ('up','down')
         and volume_koeff.is_symbol = p_id_symbol;
$function$
;

CREATE OR REPLACE FUNCTION data.exit_from_channel(p_id_symbol integer, p_start_ts bigint)
 RETURNS text
 LANGUAGE sql
AS $function$
with src as (
				select c.*,
				       rank() over(order by abs(c.o-c.c) desc)      as rnk_h,
				       abs(c.o-c.c) as abs_h,
				       rank() over(order by c.v desc)               as rnk_v,
				       row_number() over( order by c.start_ts desc) as rn
				 from(
						select ci.*
						  from data.candle ci
						 where ci.id_symbol = p_id_symbol and
						       ci.c_interval = '15' and
						       ci.start_ts is not null and
						       ci.start_ts <= p_start_ts
						 order by ci.start_ts desc limit 210
					 ) c
				 order by c.start_ts desc
		    ),
	src_checked as (select exists (select 1
					                 from src si
					                where si.rnk_h = 1 and si.rnk_v = 1 and si.rn between 3 and 6) as succ
				   ),
	src_channel as (
				   select min(s.l)                 as min_price,
				          max(s.h)                 as max_price,
				          abs(max(s.h) - min(s.l)) as ch_height,
				          avg(v)                   as avg_v
				     from src s
				    where exists(select 1 from src_checked where succ) and
				          s.rn >= 7
				   ),
	src_last   as (
				   select s.*
				     from src s
				    where exists(select 1 from src_checked where succ) and
				          s.rn = 1
				   ),
	src_pre_last as (
				   select s.*
				     from src s
				    where exists(select 1 from src_checked where succ) and
				          s.rn = 2
				   ),
	src_main_bar as (
				     select s.*
				       from src s
				      where exists(select 1 from src_checked where succ) and
				          s.rnk_h = 1
				    ),
	conds as (
				select --compare channel height and MAIN bar
				       (case
				         when b.abs_h between c.ch_height * 0.7 and c.ch_height * 2
				         then 1
				         else 0
				        end) as main_bar_h_checked,
				       -- compare directions of main bar and last bar
				       (case
				          when b.o < b.c and l.o > l.c or
				               b.o > b.c and l.o < l.c
				          then 1
				          else 0
				        end) as comp_main_last,
				        -- check size prelast and last abrs
				        (case
				          when  l.abs_h < b.abs_h/6 and
				               pl.abs_h < b.abs_h/6
				          then 1
				          else 0
				         end) as check_last_size,
				         --check avg volume and main volume
				         (case
				            when b.v > c.avg_v*8
				            then 1
				            else 0
				          end) as check_vol
				  from src_main_bar b,
				       src_pre_last pl,
				       src_last     l,
				       src_channel  c
		     )
      select (case
               when b.o < b.c then 'buy'
               else 'sell'
              end) as order_advise
        from conds c,
             src_main_bar b
       where c.main_bar_h_checked=1 and
             c.comp_main_last=1 and
             c.check_last_size=1 and
             c.check_vol=1;
$function$
;


--*******************************************************************************************************************************************



CREATE OR REPLACE FUNCTION data.get_total_wallet_balance(p_mins_back in int)
 RETURNS numeric
 LANGUAGE sql
AS $function$
/*
 Return totalwalletbalance that was "p_mins_back" minutes ago.
*/	
	with
	target as (
		   select max(ts_bybit) as max_ms,
		          (max(ts_bybit) - p_mins_back*60*1000)::bigint as target_min
		     from data.wallet_balance
	          ),
	ranked as (
		   select wb.*,row_number() over (order by abs(wb.ts_bybit - t.target_min)) as rn
		     from data.wallet_balance wb
		    cross join target t
		    where wb.ts_bybit > t.target_min - 5*60*1000 -- for eliminating full scan
	          )
	select totalwalletbalance
	from ranked
	where rn = 1;
$function$
;
 
                 
CREATE OR REPLACE FUNCTION data.get_last_price(p_id_symbol integer)
 RETURNS numeric
 LANGUAGE sql
AS $function$
/*
 Return last known C price from internal kline (external - candle)
*/	
with cndl as (
		 /* last 1m candle */
		 select max(c.id) as max_id
		   from data.candle c 
		  where c.id_symbol = p_id_symbol and 
		        c.c_interval = '1' and 
		        c.ts_db > localtimestamp - interval '5 minutes' and -- for eliminating full scan
		        round(EXTRACT(EPOCH FROM (localtimestamp - c.ts_db))) between 0 and 60
             )
select k.c
  from data.kline k
 where k.id_candle = (select max_id from cndl) and 
       k.ts_db = (
                  select max(ki.ts_db)
                    from data.kline ki 
                   where ki.id_candle = (select max_id from cndl)
                 );
$function$
;




CREATE OR REPLACE FUNCTION data.get_order_id(p_code_symbol character varying, p_side character varying, p_qty numeric)
 RETURNS int8
 LANGUAGE sql
AS $function$
/*
 Return last known orderId.
*/	
select ds.orderid 
  from (
		select ta.ts_bybit,
		       o.*,
		       row_number() over(order by ta.ts_bybit desc) as rn
		  from data.bb_order o
		  join data.trade_advice_order ta on ta.orderid = o.orderid
		  where o.side   = p_side and
		        o.symbol = p_code_symbol and 
                abs(o.qty) - abs(p_qty) < 0.6 and
                -- not yet closed
                not exists(
                           select 1
                             from data.trade_advice tai
                            where tai.to_close_orderid = o.orderid
                              and exists(
                                         select 1
                                           from data.trade_advice_order tao
                                          where tao.id_trade_advice = tai.id
                                        )
                          )
	  ) ds
 where rn=1;
$function$
;


CREATE OR REPLACE FUNCTION data.get_order_price(p_code_symbol character varying, p_side character varying, p_qty numeric)
 RETURNS numeric
 LANGUAGE sql
AS $function$
/*
 Return last known order price.
*/	
select ds.avgprice 
  from (
		select ta.ts_bybit,
		       o.*,
		       row_number() over(order by ta.ts_bybit desc) as rn
		  from data.bb_order o
		  join data.trade_advice_order ta on ta.orderid = o.orderid
		  where o.side   = p_side and
		        o.symbol = p_code_symbol and 
                abs(o.qty) - abs(p_qty) < 0.6
	  ) ds
 where rn=1;
$function$
;


CREATE OR REPLACE FUNCTION data.already_advice(p_id_symbol integer, p_order_side character varying, p_order_qty numeric)
 RETURNS boolean
 LANGUAGE sql
AS $function$
/*
 Checking whether there was a trade signal in the last 30 seconds to avoid duplication.
*/	
select 
  exists(
	select ta.*
	  from data.trade_advice ta
	  join data.trade_side ts on ts.id = ta.id_side
	  join data.trade_order_type tot on tot.id = ta.id_order_type
	 where ta.id_symbol = p_id_symbol
	   and ts.code = p_order_side
	   and ta.qty = p_order_qty
	   and tot.code = 'Market'
	   and round(EXTRACT(EPOCH FROM (localtimestamp - ta.ts_db))) < 30 -- last 30 seconds.
  )
$function$
;

CREATE OR REPLACE PROCEDURE data.log_trade(IN p_recomend character varying, IN p_id_symbol integer, IN p_ord_type character varying, IN p_module character varying, IN p_symbol_code character varying, IN p_state character varying, IN p_open_price numeric, IN p_curr_price numeric, IN p_price_div numeric, IN p_close_tpsp numeric, IN p_qty_equity numeric, IN p_order_id_to_close bigint)
 LANGUAGE sql
AS $procedure$
	INSERT INTO data.common_log(id_log_level, id_symbol, module, msg, orderid_to_close)
	VALUES (
			6,p_id_symbol,p_module,
			format(
			'[%s][%s] state = %s opened.%s.price = %s curr = %s price_div = %s close_tpsp = %s qty.equity = %s',
			 p_recomend, p_symbol_code, p_state, p_ord_type, p_open_price, p_curr_price, p_price_div, p_close_tpsp, p_qty_equity
			),
            p_order_id_to_close
	       );
$procedure$
; 


CREATE OR REPLACE FUNCTION data.get_last_closed_candle_direct(p_id_symbol integer, p_interval character varying)
 RETURNS character varying
 LANGUAGE sql
AS $function$
/*
  Return direction on last closed candle.
*/	
	select (case
	         when c.o < c.c then 'up'
	         when c.o > c.c then 'down'
	         else 'unknown'
	        end)
	  from data.candle c 
	 where c.id_symbol  = p_id_symbol and
	       c.c_interval = p_interval and
	       c.ts_db = (
						select max(ci.ts_db) as max_ts_bd
						from   data.candle ci 
						where  ci.id_symbol  = p_id_symbol and
						       ci.c_interval = p_interval and
						       ci.start_ts is not null
						group by ci.c_interval
					 );
$function$
;

CREATE OR REPLACE FUNCTION data.get_current_candle_direct(p_id_symbol integer, p_interval character varying)
 RETURNS character varying
 LANGUAGE sql
AS $function$
/*
  Return direction on last closed candle.
*/	 
select (case
         when k.o < k.c then 'up'
         when k.o > k.c then 'down'
         else 'unknown'
        end)
  from data.kline k 
  where k.id_candle = (
					select ci.id
					from   data.candle ci 
					where  ci.id_symbol  = p_id_symbol and
					       ci.c_interval = p_interval and
					       ci.start_ts is null
				 )
order by k.ts_db desc
limit 1;
$function$
;

CREATE OR REPLACE PROCEDURE data.mod_1()
 LANGUAGE plpgsql
 SECURITY definer 
AS $procedure$
declare
  l_errm             text := 'mod_1 ';
  l_message_text     text;
  l_err_code         text; 
  l_excp_context     text;
--------------------------------------------------------
  l_mod              varchar(100) := 'mod_1';
  c_info    CONSTANT int := 1;
  c_fatal   CONSTANT int := 5;
  c_trade   CONSTANT int := 6;
--------------------------------------------------------
  c_buy      CONSTANT int := 1;
  c_sell     CONSTANT int := 2;
  c_market   CONSTANT int := 1;
  c_spot     CONSTANT int := 1;
  c_baseCoin CONSTANT int := 1;
--------------------------------------------------------
  m_work_id_symbol    int := 5;
  l_id_symbol         int := null;
  l_wb_diff_sec_ct    int;
  rec_coin_balance    record;
  rec_trade_meta      data.trade_meta%rowtype;
  l_closed_1m         varchar(12);
  l_closed_5m         varchar(12);
  l_current_1m        varchar(12);
  l_current_5m        varchar(12);
begin	
  SET TIME ZONE 'UTC';

  -- check that balance is actual
  select coalesce(60-round(EXTRACT(EPOCH FROM (LOCALTIMESTAMP-to_timestamp(max(wb.ts_bybit)::double precision/1000.0)))),-1) as next_update_after_sec
    into l_wb_diff_sec_ct
    from data.wallet_balance wb
   where exists(
                select 1 
                  from data.wallet_balance_coin wbc
                 where wbc.id_wallet_balance = wb.id
               );

  if not(l_wb_diff_sec_ct between 1 and 80) then
    insert into data.common_log(id_log_level, id_symbol, module, msg) 
                         values(c_info,       l_id_symbol, l_mod, l_wb_diff_sec_ct::text);
  elseif l_wb_diff_sec_ct = -1 then
   raise exception 'do not have acutal wallet balance';
  end if;

  begin
   	select s.id   as symbol_id, 
	       s.code as symbol_code,
	       c.code,  
	       TRUNC(wbc.equity::numeric, 2) as equity, 
	       wb.totalwalletbalance
    into strict rec_coin_balance
	from data.wallet_balance_coin wbc
	join data.coin c on c.id = wbc.id_coin
	join data.symbol s on s.id_coin = c.id
	join data.wallet_balance wb on wb.id = wbc.id_wallet_balance
	where wbc.id_wallet_balance = (select max(wb.id) from data.wallet_balance wb )
	  and c.id != 7 /* exclude USDT */
	  and abs(wbc.equity) > 0.1 
	  and s.id = m_work_id_symbol; 

    select *
      into rec_trade_meta     
      from data.trade_meta tm
     where tm.id_symbol = rec_coin_balance.symbol_id; 

	select 
	  data.get_last_closed_candle_direct(p_id_symbol => m_work_id_symbol, p_interval => '1') as closed_1m, 
	  data.get_last_closed_candle_direct(p_id_symbol => m_work_id_symbol, p_interval => '5') as closed_5m,
	  data.get_current_candle_direct(p_id_symbol     => m_work_id_symbol, p_interval => '1') as current_1m,
	  data.get_current_candle_direct(p_id_symbol     => m_work_id_symbol, p_interval => '5') as current_5m
    into l_closed_1m,
         l_closed_5m,
         l_current_1m,
         l_current_5m;

    if l_closed_1m='up' and l_closed_5m='up' and l_current_1m='up' and l_current_5m='up' then
    insert into data.common_log(id_log_level, id_symbol,        module, msg) 
                         values(c_info,       m_work_id_symbol, l_mod,  'ADIVE Buy - all Up');
    end if;

    if l_closed_1m='down' and l_closed_5m='down' and l_current_1m='down' and l_current_5m='down' then
    insert into data.common_log(id_log_level, id_symbol,        module, msg) 
                         values(c_info,       m_work_id_symbol, l_mod,  'ADIVE Sell - all down');
    end if;
             /*
               insert into data.trade_advice(id_symbol,   
                                             id_category,
                                             id_side, 
                                             isLeverage,
                                             id_order_type, 
                                             qty,                           
                                             id_market_unit, 
                                             who_insert, 
                                             to_close_orderid)  
                                      values(l_id_symbol, 
                                             c_spot,     
                                             c_sell,  
                                             false,      
                                             c_market,     
                                             abs(rec_coin_balance.equity) , 
                                             c_baseCoin,
                                             'CLOSE_ADVICE_MOD1',
                                             l_order_id_to_close);
                */
  exception
  when no_data_found then
    insert into data.common_log(id_log_level, id_symbol,        module, msg)  
                         values(c_info,       m_work_id_symbol, l_mod,  'OK, balance not empty - no_data_found'); 
  when too_many_rows then
    insert into data.common_log(id_log_level, id_symbol,        module, msg)  
                         values(c_info,       m_work_id_symbol, l_mod,  'ERROR, too_many_rows in balance'); 
  end;

exception
when others then 
    GET STACKED DIAGNOSTICS
      l_message_text = MESSAGE_TEXT,
      l_err_code     = RETURNED_SQLSTATE,
      l_excp_context := PG_EXCEPTION_CONTEXT;  
   insert into data.common_log(id_log_level, id_symbol, module, msg) 
                        values(c_fatal,      l_id_symbol, l_mod,
                               l_err_code ||' - '|| l_message_text ||' - '|| l_excp_context ||' - '|| l_errm
                       ); 
END;
$procedure$
;
 

-- DROP PROCEDURE "data".mod_1();

CREATE OR REPLACE PROCEDURE data.mod_1()
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $procedure$
declare
  l_errm             text := 'mod_1 ';
  l_message_text     text;
  l_err_code         text; 
  l_excp_context     text;
--------------------------------------------------------
  l_mod              varchar(100) := 'mod_1';
  c_info    CONSTANT int := 1;
  c_fatal   CONSTANT int := 5;
  c_trade   CONSTANT int := 6;
--------------------------------------------------------
  c_buy      CONSTANT int := 1;
  c_sell     CONSTANT int := 2;
  c_market   CONSTANT int := 1;
  c_spot     CONSTANT int := 1;
  c_baseCoin CONSTANT int := 1;
--------------------------------------------------------
  m_work_id_symbol    int := 5;
  l_id_symbol         int := null;
  l_wb_diff_sec_ct    int;
  rec_coin_balance    record;
  rec_trade_meta      data.trade_meta%rowtype;
  l_closed_1m         varchar(12);
  l_closed_5m         varchar(12);
  l_current_1m        varchar(12);
  l_current_5m        varchar(12);
begin	
  SET TIME ZONE 'UTC';

  -- check that balance is actual
  select coalesce(60-round(EXTRACT(EPOCH FROM (LOCALTIMESTAMP-to_timestamp(max(wb.ts_bybit)::double precision/1000.0)))),-1) as next_update_after_sec
    into l_wb_diff_sec_ct
    from data.wallet_balance wb
   where exists(
                select 1 
                  from data.wallet_balance_coin wbc
                 where wbc.id_wallet_balance = wb.id
               );

  if not(l_wb_diff_sec_ct between 1 and 80) then
    insert into data.common_log(id_log_level, id_symbol, module, msg) 
                         values(c_info,       l_id_symbol, l_mod, l_wb_diff_sec_ct::text);
  elseif l_wb_diff_sec_ct = -1 then
   raise exception 'do not have acutal wallet balance';
  end if;

  begin
   	select s.id   as symbol_id, 
	       s.code as symbol_code,
	       c.code,  
	       TRUNC(wbc.equity::numeric, 2) as equity, 
	       wb.totalwalletbalance
    into strict rec_coin_balance
	from data.wallet_balance_coin wbc
	join data.coin c on c.id = wbc.id_coin 
	join data.symbol s on s.id_coin = c.id
	join data.wallet_balance wb on wb.id = wbc.id_wallet_balance
	where wbc.id_wallet_balance = (select max(wb.id) from data.wallet_balance wb )
	  and c.id != 7 /* exclude USDT */
	  and abs(wbc.equity) > 0.1 
	  and s.id = m_work_id_symbol; 

      insert into data.common_log(id_log_level, id_symbol,        module, msg)  
                         values(c_info,       m_work_id_symbol, l_mod,  'OK, balance not empty - no_data_found'); 
  exception
  when no_data_found then
    select *
      into rec_trade_meta     
      from data.trade_meta tm
     where tm.id_symbol = m_work_id_symbol; 

	select 
	  data.get_last_closed_candle_direct(p_id_symbol => m_work_id_symbol, p_interval => '1') as closed_1m, 
	  data.get_last_closed_candle_direct(p_id_symbol => m_work_id_symbol, p_interval => '5') as closed_5m,
	  data.get_current_candle_direct(p_id_symbol     => m_work_id_symbol, p_interval => '1') as current_1m,
	  data.get_current_candle_direct(p_id_symbol     => m_work_id_symbol, p_interval => '5') as current_5m
    into l_closed_1m,
         l_closed_5m,
         l_current_1m,
         l_current_5m; 

    if l_closed_1m='up' and l_closed_5m='up' and l_current_1m='up' and l_current_5m='up' then 
    insert into data.common_log(id_log_level, id_symbol,        module, msg) 
                         values(c_info,       m_work_id_symbol, l_mod,  'ADIVICE Buy - all Up');
	     if data.already_advice(p_id_symbol => m_work_id_symbol, p_order_side => 'Buy', p_order_qty => rec_trade_meta.qty) then
				   insert into data.common_log(id_log_level, id_symbol,        module, msg) 
				                        values(c_info,       m_work_id_symbol, l_mod,  'ALREADY ADIVICE Buy - all up');
         else
               insert into data.trade_advice(id_symbol,   
                                             id_category,
                                             id_side, 
                                             isLeverage,
                                             id_order_type,  
                                             qty,                           
                                             id_market_unit, 
                                             who_insert)  
                                      values(m_work_id_symbol, 
                                             c_spot,     
                                             c_buy,  
                                             false,      
                                             c_market,     
                                             rec_trade_meta.qty,  
                                             c_baseCoin,
                                             l_mod);
         end if;
    elseif l_closed_1m='down' and l_closed_5m='down' and l_current_1m='down' and l_current_5m='down' then
    insert into data.common_log(id_log_level, id_symbol,        module, msg) 
                         values(c_info,       m_work_id_symbol, l_mod,  'ADIVICE Sell - all down');
	     if data.already_advice(p_id_symbol => m_work_id_symbol, p_order_side => 'Sell', p_order_qty => rec_trade_meta.qty) then
				   insert into data.common_log(id_log_level, id_symbol,        module, msg) 
				                        values(c_info,       m_work_id_symbol, l_mod,  'ALREADY ADIVICE Sell - all down');
	     else
	               insert into data.trade_advice(id_symbol,   
	                                             id_category,
	                                             id_side, 
	                                             isLeverage,
	                                             id_order_type, 
	                                             qty,                           
	                                             id_market_unit, 
	                                             who_insert)  
	                                      values(m_work_id_symbol, 
	                                             c_spot,     
	                                             c_sell,  
	                                             false,      
	                                             c_market,     
	                                             rec_trade_meta.qty, 
	                                             c_baseCoin,
	                                             l_mod);
	    end if;
    else 
    insert into data.common_log(id_log_level, id_symbol,        module, msg) 
                         values(c_info,       m_work_id_symbol, l_mod,  'NO ADIVICE - no direction');
    end if; 
  when too_many_rows then
    insert into data.common_log(id_log_level, id_symbol,        module, msg)  
                         values(c_info,       m_work_id_symbol, l_mod,  'ERROR, too_many_rows in balance'); 
  end;

exception
when others then 
    GET STACKED DIAGNOSTICS
      l_message_text = MESSAGE_TEXT,
      l_err_code     = RETURNED_SQLSTATE,
      l_excp_context := PG_EXCEPTION_CONTEXT;  
   insert into data.common_log(id_log_level, id_symbol, module, msg) 
                        values(c_fatal,      l_id_symbol, l_mod,
                               l_err_code ||' - '|| l_message_text ||' - '|| l_excp_context ||' - '|| l_errm
                       ); 
END;
$procedure$
;
 


CREATE OR REPLACE PROCEDURE data.order_to_close()
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $procedure$
declare
  l_errm             text := 'order_to_close ';
  l_message_text     text;
  l_err_code         text; 
  l_excp_context     text;
--------------------------------------------------------
  l_mod              varchar(100) := 'order_to_close';
  c_info    CONSTANT int := 1;
  c_fatal   CONSTANT int := 5;
  c_trade   CONSTANT int := 6;
--------------------------------------------------------
  c_buy      CONSTANT int := 1;
  c_sell     CONSTANT int := 2;
  c_market   CONSTANT int := 1;
  c_spot     CONSTANT int := 1;
  c_baseCoin CONSTANT int := 1;
--------------------------------------------------------
  l_id_symbol         int := null;
  l_wb_diff_sec_ct    int;
  rec_coin_balance    record;
  rec_trade_meta      data.trade_meta%rowtype;
  l_order_price       numeric;
  l_curr_price        numeric;
  l_price_div         numeric;
  l_order_state       varchar(7) := 'unknown'; -- profit, loss
  l_order_id_to_close int8;
begin	
  SET TIME ZONE 'UTC';

  -- check that balance is actual
  select coalesce(60-round(EXTRACT(EPOCH FROM (LOCALTIMESTAMP-to_timestamp(max(wb.ts_bybit)::double precision/1000.0)))),-1) as next_update_after_sec
    into l_wb_diff_sec_ct
    from data.wallet_balance wb
   where exists(
                select 1 
                  from data.wallet_balance_coin wbc
                 where wbc.id_wallet_balance = wb.id
               );

  if not(l_wb_diff_sec_ct between 1 and 80) then
    insert into data.common_log(id_log_level, id_symbol, module, msg) 
                         values(c_info,       l_id_symbol, l_mod, l_wb_diff_sec_ct::text);
  elseif l_wb_diff_sec_ct = -1 then
   raise exception 'do not have acutal wallet balance';
  end if;
  
  for rec_coin_balance in (
						   	select s.id   as symbol_id, 
                                   s.code as symbol_code,
                                   c.code,  
                                   TRUNC(wbc.equity::numeric, 2) as equity, 
                                   wb.totalwalletbalance
							from data.wallet_balance_coin wbc
							join data.coin c on c.id = wbc.id_coin
                            join data.symbol s on s.id_coin = c.id
							join data.wallet_balance wb on wb.id = wbc.id_wallet_balance
							where wbc.id_wallet_balance = (select max(wb.id) from data.wallet_balance wb )
                              and c.id != 7 /* exclude USDT */
                              and abs(wbc.equity) > 0.1  
						  )
  loop
    l_id_symbol := rec_coin_balance.symbol_id;

    insert into data.common_log(id_log_level, id_symbol, module, msg)  
                         values(c_info,      l_id_symbol, l_mod,
                               format('Total balance %s Current coin %s balance is %s', 
                                 rec_coin_balance.totalwalletbalance, 
                                 rec_coin_balance.code, 
                                 rec_coin_balance.equity)
                               ); 

    select *
      into rec_trade_meta     
      from data.trade_meta tm
     where tm.id_symbol = rec_coin_balance.symbol_id; 

    -- get buy/sell opened order price by this symbol.
	select data.get_order_price(p_code_symbol => rec_coin_balance.symbol_code,
		                               p_side => (case 
                                                   when rec_coin_balance.equity > 0 
                                                     then 'Buy'
                                                     else 'Sell' 
                                                  end),
		                                p_qty => rec_coin_balance.equity) into l_order_price;

    select data.get_order_id(p_code_symbol => rec_coin_balance.symbol_code, 
                             p_side        => (case 
                                                   when rec_coin_balance.equity > 0 
                                                     then 'Buy'
                                                     else 'Sell' 
                                               end), 
                             p_qty => rec_coin_balance.equity)
    into l_order_id_to_close;  

    -- 1. get last price.
    select data.get_last_price(p_id_symbol => rec_coin_balance.symbol_id) into l_curr_price;
    
    if rec_coin_balance.equity > 0 then 
      -- ************************************************************************
      if l_curr_price < l_order_price then 
        l_order_state := 'loss';
      elseif l_curr_price > l_order_price then
        l_order_state := 'profit';
      end if;     
      l_price_div := abs(l_curr_price - l_order_price);

      if l_price_div > rec_trade_meta.tpsl_diff then
        -- fixing profit/loss
			call data.log_trade('CLOSE',l_id_symbol,'Buy',l_mod,rec_coin_balance.symbol_code, l_order_state, l_order_price, l_curr_price, l_price_div,
								rec_trade_meta.tpsl_diff,
								rec_coin_balance.equity,l_order_id_to_close 
			                   );
	       if not(data.already_advice(p_id_symbol => l_id_symbol, p_order_side => 'Sell', p_order_qty => abs(rec_coin_balance.equity) )) then
	        -- save advice to close opened SELL order.
			call data.log_trade('ADVICE CLOSE',l_id_symbol,'Buy',l_mod,rec_coin_balance.symbol_code, l_order_state, l_order_price, l_curr_price, l_price_div,
								rec_trade_meta.tpsl_diff,
								rec_coin_balance.equity,l_order_id_to_close 
			                   );
               insert into data.trade_advice(id_symbol,   
                                             id_category,
                                             id_side, 
                                             isLeverage,
                                             id_order_type, 
                                             qty,                           
                                             id_market_unit, 
                                             who_insert, 
                                             to_close_orderid)  
                                      values(l_id_symbol, 
                                             c_spot,     
                                             c_sell,  
                                             false,      
                                             c_market,     
                                             abs(rec_coin_balance.equity) , 
                                             c_baseCoin,
                                             'CLOSE_ADVICE_MOD1',
                                             l_order_id_to_close);
	      else
			call data.log_trade('ALREADY ADVICED - SKIP',l_id_symbol,'Buy',l_mod,rec_coin_balance.symbol_code, l_order_state, l_order_price, l_curr_price, l_price_div,
								rec_trade_meta.tpsl_diff,
								rec_coin_balance.equity,l_order_id_to_close 
			                   );
          end if; 
      else
			call data.log_trade('HOLD',l_id_symbol,'Buy',l_mod,rec_coin_balance.symbol_code, l_order_state, l_order_price, l_curr_price, l_price_div,
								rec_trade_meta.tpsl_diff,
								rec_coin_balance.equity,l_order_id_to_close 
			                   );
      end if;

    -- ************************************************************************
    elsif rec_coin_balance.equity < 0 then
    -- can BUY - the coin is sold 
      if l_curr_price < l_order_price then
        l_order_state := 'profit';
      elseif l_curr_price > l_order_price then 
        l_order_state := 'loss';
      end if;     
      l_price_div := abs(l_curr_price - l_order_price);

      if l_price_div > rec_trade_meta.tpsl_diff then
        -- fixing profit/loss
			call data.log_trade('CLOSE',l_id_symbol,'Sell',l_mod,rec_coin_balance.symbol_code, l_order_state, l_order_price, l_curr_price, l_price_div,
								rec_trade_meta.tpsl_diff,
								rec_coin_balance.equity,l_order_id_to_close 
			                   );
	       if not(data.already_advice(p_id_symbol => l_id_symbol, p_order_side => 'Buy', p_order_qty => abs(rec_coin_balance.equity) )) then
	        -- save advice to close opened SELL order.
			call data.log_trade('ADVICE CLOSE',l_id_symbol,'Sell',l_mod,rec_coin_balance.symbol_code, l_order_state, l_order_price, l_curr_price, l_price_div,
								rec_trade_meta.tpsl_diff,
								rec_coin_balance.equity,l_order_id_to_close 
			                   );
               insert into data.trade_advice(id_symbol,   
                                             id_category,
                                             id_side, 
                                             isLeverage,
                                             id_order_type, 
                                             qty,                           
                                             id_market_unit, 
                                             who_insert, 
                                             to_close_orderid)  
                                      values(l_id_symbol, 
                                             c_spot,     
                                             c_buy,   
                                             false,      
                                             c_market,     
                                             abs(rec_coin_balance.equity) , 
                                             c_baseCoin,
                                             'CLOSE_ADVICE_MOD1',
                                             l_order_id_to_close);
	      else
			call data.log_trade('ALREADY ADVICED - SKIP',l_id_symbol,'Sell',l_mod,rec_coin_balance.symbol_code, l_order_state, l_order_price, l_curr_price, l_price_div,
								rec_trade_meta.tpsl_diff,
								rec_coin_balance.equity,l_order_id_to_close 
			                   );
          end if;
      else
			call data.log_trade('HOLD',l_id_symbol,'Sell',l_mod,rec_coin_balance.symbol_code, l_order_state, l_order_price, l_curr_price, l_price_div,
								rec_trade_meta.tpsl_diff,
								rec_coin_balance.equity,l_order_id_to_close 
			                   );
      end if;
    -- ************************************************************************

    end if;  

  end loop;
exception
when others then 
    GET STACKED DIAGNOSTICS
      l_message_text = MESSAGE_TEXT,
      l_err_code     = RETURNED_SQLSTATE,
      l_excp_context := PG_EXCEPTION_CONTEXT;  
   insert into data.common_log(id_log_level, id_symbol, module, msg) 
                        values(c_fatal,      l_id_symbol, l_mod,
                               l_err_code ||' - '|| l_message_text ||' - '|| l_excp_context ||' - '|| l_errm
                       ); 
END;
$procedure$
; 



EOSQL
