To run only Postgres db, use command
$ docker compose up -d postgres_db liquibase

Success result output is (example):

$ docker compose up -d postgres_db liquibase
[+] Running 4/4
 ✔ Network dock_appnet         Created                                                                                                                                                                 0.1s 
 ✔ Volume "dock_pg_data"       Created                                                                                                                                                                 0.0s 
 ✔ Container pg_container      Healthy                                                                                                                                                                 5.7s 
 ✔ Container dock-liquibase-1  Started                                                                                                                                                                 5.7s 
$ docker logs dock-liquibase-1
####################################################
##   _     _             _ _                      ##
##  | |   (_)           (_) |                     ##
##  | |    _  __ _ _   _ _| |__   __ _ ___  ___   ##
##  | |   | |/ _` | | | | | '_ \ / _` / __|/ _ \  ##
##  | |___| | (_| | |_| | | |_) | (_| \__ \  __/  ##
##  \_____/_|\__, |\__,_|_|_.__/ \__,_|___/\___|  ##
##              | |                               ##
##              |_|                               ##
##                                                ## 
##  Get documentation at docs.liquibase.com       ##
##  Get certified courses at learn.liquibase.com  ## 
##                                                ##
####################################################
Starting Liquibase at 19:06:08 using Java 17.0.12 (version 4.29.2 #3683 built at 2024-08-29 16:45+0000)
Liquibase Version: 4.29.2
Liquibase Open Source 4.29.2 by Liquibase
Running Changeset: db.coin.xml::2026_09_01-1-create-coin::yakushev
Running Changeset: db.coin.xml::2026_09_01-2-populate-coin::yakushev
Running Changeset: db.symbol.xml::2026_09_01-1-create-symbol::yakushev
Running Changeset: db.symbol.xml::2026_09_01-2-populate-symbol::yakushev
Running Changeset: db.trade_category.xml::2026_09_01-1-create-trade_category::yakushev
Running Changeset: db.trade_category.xml::2026_09_01-1-populate-trade_category::yakushev
Running Changeset: db.trade_side.xml::2026_09_01-1-create-trade_side::yakushev
Running Changeset: db.trade_side.xml::2026_09_01-2-populate-trade_side::yakushev
Running Changeset: db.trade_order_type.xml::2026_09_01-1-create-trade_order_type::yakushev
Running Changeset: db.trade_order_type.xml::2026_09_01-2-populate-trade_order_type::yakushev
Running Changeset: db.market_unit.xml::2026_09_01-1-create-market_unit::yakushev
Running Changeset: db.market_unit.xml::2026_09_01-2-populate-market_unit::yakushev
Running Changeset: db.v_symbols.xml::2026_09_01-1-create-view-v_symbols::yakushev
Running Changeset: db.trade_advice.xml::2026_09_01-1-create-trade_advice::yakushev
Running Changeset: db.trade_advice_order.xml::2026_09_01-1-create-trade_advice_order::yakushev
Running Changeset: db.bb_order.xml::2026_09_01-1-create-bb_order::yakushev
Running Changeset: db.v_trade_advice.xml::2026_09_01-1-create-view-v_trade_advice::yakushev
Running Changeset: db.order_book_snapshot.xml::2026_09_01-1-create-order_book_snapshot::yakushev
Running Changeset: db.order_book.xml::2026_09_01-1-create-order_book::yakushev
Running Changeset: db.open_interest.xml::2026_09_01-1-create-open_interest::yakushev
Running Changeset: db.candle.xml::2026_09_01-1-create-candle::yakushev
Running Changeset: db.kline.xml::2026_09_01-1-create-kline::yakushev
Running Changeset: db.interval.xml::2026_09_01-1-create-interval::yakushev
Running Changeset: db.interval.xml::2026_09_01-2-populate-interval::yakushev
Running Changeset: db.ref_symbols_interval.xml::2026_09_01-1-create-ref_symbols_interval::yakushev
Running Changeset: db.ref_symbols_interval.xml::2026_09_01-1-populate-ref_symbols_interval::yakushev
Running Changeset: db.wallet_balance.xml::2026_09_01-1-create-wallet_balance::yakushev
Running Changeset: db.wallet_balance_coin.xml::2026_09_01-1-create-wallet_balance_coin::yakushev
Running Changeset: db.ref_symbols_intervals.xml::2026_09_01-1-create-view-ref_symbol_intervals::yakushev
Running Changeset: db.symbols_ob_stat.xml::2026_09_01-1-create-view-symbols_ob_stat::yakushev
Running Changeset: db.trade_meta.xml::2026_09_01-1-create-trade_meta::yakushev
Running Changeset: db.bb_log.xml::2026_09_01-1-create-bb_log::yakushev
Running Changeset: db.log_level.xml ::2026_09_01-1-craete-log_level::yakushev
Running Changeset: db.log_level.xml ::2026_09_01-2-populate-log_level::yakushev
Running Changeset: db.common_log.xml::2026_09_01-1-create-common_log::yakushev
Running Changeset: db.reglament.xml ::2026_09_01-1-create-reglament::yakushev
Running Changeset: db.reglament.xml ::2026_09_01-2-populate-reglament::yakushev
Running Changeset: db.reglament_log.xml::2026_09_01-1-create-reglament_log::yakushev
Running Changeset: db.advice_meta.xml::2026_09_01-1-create-advice_meta::yakushev
Running Changeset: db.advice_meta.xml::2026_09_01-2-populate-advice_meta::yakushev
Running Changeset: db.ref_advice_meta_interval.xml::2026_09_01-1-create-ref_advice_meta_interval::yakushev
Running Changeset: db.ref_advice_meta_interval.xml::2026_09_01-2-populate-ref_advice_meta_interval::yakushev
Running Changeset: db.advice.xml::2026_09_01-1-create-advice::yakushev
Running Changeset: db.v_curr_state.xml::2026_09_01-1-create-v_curr_state::yakushev
Running Changeset: db.view_deep.xml::2026_09_01-1-create-view_deep::yakushev
Running Changeset: db.call_advice_function.xml::2026_09_01-1-create-call_advice_function::yakushev
Running Changeset: db.bounce_turn.xml::2026_09_01-1-create-bounce_turn::yakushev
Running Changeset: db.exit_from_channel.xml::2026_09_01-1-create-exit_from_channel::yakushev
Running Changeset: db.get_total_wallet_balance.xml::2026_09_01-1-create-get_total_wallet_balance::yakushev
Running Changeset: db.get_last_price.xml::2026_09_01-1-create-get_last_price::yakushev
Running Changeset: db.get_order_id.xml::2026_09_01-1-create-get_order_id::yakushev
Running Changeset: db.get_order_price.xml::2026_09_01-1-create-get_order_price::yakushev
Running Changeset: db.already_advice.xml::2026_09_01-1-create-already_advice::yakushev
Running Changeset: db.log_trade.xml::2026_09_01-1-create-log_trade::yakushev
Running Changeset: db.get_last_closed_candle_direct.xml::2026_09_01-1-create-get_last_closed_candle_direct::yakushev
Running Changeset: db.get_current_candle_direct.xml::2026_09_01-1-create-get_current_candle_direct::yakushev

UPDATE SUMMARY
Run:                         56
Previously run:               0
Filtered out:                 0
-------------------------------
Total change sets:           56

Liquibase: Update has been successful. Rows affected: 162
Liquibase command 'update' was executed successfully.
$ 


