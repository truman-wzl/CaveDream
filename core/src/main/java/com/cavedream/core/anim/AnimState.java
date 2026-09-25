package com.cavedream.core.anim;

/** 动画状态（由玩家物理输入派生；一次性动作走 Animator 的 overlay，不在此枚举里循环）。 */
public enum AnimState {
    IDLE, WALK, RUN, JUMP, FALL, SWIM
}
