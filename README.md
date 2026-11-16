# RSMOD
适用于滚动的天空(TapTap版)的破解模块，使用DeepSeek-v4f协助开发

### 功能列表
- 去除完整性校验
- 去除实名认证/防沉迷
- 去除TapTap登录
- 去除防沉迷支付限额
- 内购破解
- 跳过广告
- 角色无敌
- RVA Dumper

### 游戏 5.6.8 更新
- 新的 RVA（顺序即目标表顺序，共 13 项）：

  | # | 目标 | RVA | 补丁 |
  | --- | --- | --- | --- |
  | 0 | PropsShield::TakeShield | 0x124c584 | ret |
  | 1 | PropUtil::Set_Shield | 0x15c6a3c | ret（默认关） |
  | 2 | PropUtil::Set_Levitate | 0x15c6d0c | ret（默认关） |
  | 3 | PropFloating::ResetFloating | 0xcc3bf4 | ret |
  | 4 | PropFloating::OnTriggerExit | 0xcc3a78 | ret |
  | 5 | PropsShield::resetShield | 0x124c2f0 | ret |
  | 6 | PropsShield::get_IsUseingShield | 0x124bc4c | mov w0,#1;ret |
  | 7 | PropsShield::get_IsTrigger | 0x124bcd4 | mov w0,#1;ret |
  | 8 | PropUtil::get_Get_Shield | 0x15c6918 | movz w0,#9999;ret |
  | 9 | PropLevitate::get_IsUseingLevitate | 0x124b634 | mov w0,#1;ret |
  | 10 | PropLevitate::get_IsTrigger | 0x124b6bc | mov w0,#1;ret |
  | 11 | PropLevitate::TakeLevitate | 0x124b970 | ret |
  | 12 | PropUtil::get_Get_Levitate | 0x15c6be8 | movz w0,#9999;ret |
