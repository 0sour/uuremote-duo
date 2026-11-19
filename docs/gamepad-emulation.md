# 手柄映射（桌面 / FPS / 模拟器）

> 状态：本地输入层与 JVM 单测完成；远端实际输入待真机验收。
> 目标：没有官方 PC 端键鼠模拟时，也能用实体手柄完成桌面点击、FPS 移动和模拟器按键。
> 不做虚拟按键、宏编辑器和轮盘。

## 入口

- 全局设置 → 外设 → 手柄映射。
- 串流内控制中心 → 外设 → 手柄映射。
- 取值：关闭 / 桌面 / FPS / 模拟器。默认关闭，关闭时保持原有 XInput 直通。

## 实现

- `GamepadEmulation` 是无 Android 依赖的纯映射类，输入 `GamepadControl`、摇杆轴和扳机值，
  输出 `EmulatedAction`：
  - `Key(vk, down)` → `SessionController.key`
  - `MouseButton(button, down)` → `mouseButton`
  - `MouseMove(dx, dy)` → `mouseRelative`
  - `Scroll(dx, dy)` → `scroll`
- 这些动作就是触摸板/键盘已经在用的 RPC，因此不需要被控端额外能力。
- 开启映射时不再启动 `gpd_connect`；关闭映射后恢复直通（若「串流默认」允许）。
- 切换模式、开启仅观看、打开本地菜单/IME 时调用 `releaseAll`，释放映射持有的按键和鼠标键。
- 摇杆死区固定 18%，超出死区后线性缩放；鼠标速度约 16 px/事件，滚轮 1.5 格/事件。

## 桌面模板

| 输入 | 动作 |
| --- | --- |
| 左摇杆 | 鼠标移动 |
| 右摇杆 | 滚轮 |
| A / B / X | 左键 / 右键 / 中键 |
| Y、START | 回车 |
| LB / RB | 浏览器后退 / 前进 |
| 十字键 | 方向键 |
| SELECT | Esc |

## FPS 模板

| 输入 | 动作 |
| --- | --- |
| 左摇杆 | W / A / S / D |
| 右摇杆 | 鼠标移动 |
| LT / RT | 右键（瞄准）/ 左键（开火） |
| A / B / X / Y | 空格 / Ctrl / R / E |
| LB / RB | Shift / Tab |
| L3 / R3 | Ctrl / Shift |
| 十字键 | 1 / 2 / 3 / 4 |
| START / SELECT | Esc / Tab |

## 模拟器模板

| 输入 | 动作 |
| --- | --- |
| A / B / X / Y | Z / X / A / S |
| LB / RB | Q / W |
| 十字键、左摇杆 | 方向键 |
| START / SELECT | 回车 / Shift |

## 限制

- 映射是按键与相对鼠标事件，不模拟 XInput 手柄；需要手柄原生输入的游戏请保持「关闭」。
- 没有连发、宏、按键组合录制；组合键请用触摸板快捷键或内置键盘。
- 摇杆鼠标速度没有独立设置项，后续可加入灵敏度滑杆。
- 远端验证前，先在设置里切换模式并确认原有手柄直通已释放。
