"""Business-specific preference validation. No model/agent runtime is implemented here."""

import re
from decimal import Decimal

from pydantic import BaseModel, ConfigDict, Field

from sky_ai.text import contains_any


class Preferences(BaseModel):
    model_config = ConfigDict(extra="forbid")
    minPrice: Decimal | None = Field(None, ge=0, max_digits=7, decimal_places=2)
    maxPrice: Decimal | None = Field(None, gt=0, max_digits=7, decimal_places=2)
    peopleCount: int | None = Field(None, ge=1, le=20)
    budgetScope: str | None = None
    spicyLevelMin: int | None = Field(None, ge=0, le=4)
    spicyLevelMax: int | None = Field(None, ge=0, le=4)
    sweetnessLevelMax: int | None = Field(None, ge=0, le=4)
    preferredTags: list[str] = Field(default_factory=list)
    excludedTags: list[str] = Field(default_factory=list)
    allergens: list[str] = Field(default_factory=list)
    categoryId: int | None = Field(None, gt=0)

    def clarification(self):
        """检查预算口径、用餐人数及条件冲突；需要补充时返回追问文案，否则返回 None。"""
        if self.maxPrice is not None and self.budgetScope is None:
            return "这个预算是单个菜品、人均预算，还是这顿饭的总预算？总预算请同时告诉我用餐人数。"
        if (
            self.maxPrice is not None
            and self.budgetScope in {"total", "per_person"}
            and not self.peopleCount
        ):
            return "请告诉我用餐人数，我会按人数和总预算搭配整餐。"
        if (
            self.minPrice is not None
            and self.maxPrice is not None
            and self.minPrice > self.maxPrice
        ):
            return "最低价格高于最高预算，请调整价格范围。"
        minimum_spicy_level = self.spicyLevelMin
        if not minimum_spicy_level:
            minimum_spicy_level = 0
        if "清淡" in self.preferredTags and minimum_spicy_level >= 3:
            return "清淡和重辣有冲突，请告诉我优先清淡还是辣味。"
        return None

    def tool_request(self):
        """将已确认偏好转换为工具名称和请求参数，人均预算乘以人数后用于整餐推荐。"""
        data = self.model_dump(exclude={"peopleCount", "budgetScope"}, exclude_none=True)
        if self.budgetScope in {"total", "per_person"} and self.maxPrice and self.peopleCount:
            if self.budgetScope == "per_person":
                budget = self.maxPrice * self.peopleCount
            else:
                budget = self.maxPrice
            data.pop("minPrice", None)
            data.pop("maxPrice", None)
            data["totalBudget"] = json_money(budget)
            data["peopleCount"] = self.peopleCount
            return "recommend_meal_combo", data
        for field in ("minPrice", "maxPrice"):
            if field in data:
                data[field] = json_money(data[field])
        data["limit"] = 5
        return "recommend_dishes", data


def json_money(value: Decimal):
    """把精确 Decimal 转成无二进制计算误差的 JSON 数值表示。"""
    if value == value.to_integral_value():
        return int(value)
    return float(format(value, "f"))


TAGS = ("下饭", "肉类", "素食", "清淡", "川味", "低油", "高蛋白", "低卡", "热菜", "凉菜")
ALLERGENS = (
    "花生",
    "坚果",
    "牛奶",
    "乳制品",
    "鸡蛋",
    "海鲜",
    "虾",
    "蟹",
    "鱼",
    "大豆",
    "小麦",
    "芝麻",
)


def extract_preferences(previous: dict, message: str) -> Preferences:
    """从本轮文本提取预算、人数和口味，合并历史偏好并返回经过校验的 Preferences。

    previous 为同一会话的历史偏好字典；过敏原累计保留，预算和口味可按明确表达覆盖。
    """
    data = Preferences.model_validate(previous).model_dump()
    text = re.sub(r"\s+", "", message)
    amount = r"(\d{1,5}(?:\.\d{1,2})?)"
    scopes = [
        (rf"(?:单个菜|每个菜|一道菜|单菜)(?:预算|最高|不超过|最多)?{amount}", "dish"),
        (rf"(?:人均|每人)(?:预算|最高|不超过|最多)?{amount}", "per_person"),
        (rf"{amount}元(?:以内|以下|封顶)", "dish"),
        (rf"(?:预算|控制在|不超过|最多)(?:是|为|[:：])?{amount}", None),
        (rf"(?:我有|手里有|带了){amount}元", None),
    ]
    for pattern, scope in scopes:
        match = re.search(pattern, text)
        if match:
            data.update(maxPrice=Decimal(match[1]), budgetScope=scope)
            break
    if contains_any(text, ("总预算", "这顿饭", "整顿饭", "一共", "合计")):
        data["budgetScope"] = "total"
    elif contains_any(text, ("单个菜", "每个菜", "一道菜", "单菜")):
        data["budgetScope"] = "dish"
    elif "人均" in text or "每人" in text:
        data["budgetScope"] = "per_person"
    match = re.search(r"(\d{1,2})个?人|([一二两三四五六七八九十])个?人", text)
    if match:
        if match[1]:
            people = int(match[1])
        else:
            chinese_numbers = {
                "一": 1,
                "二": 2,
                "两": 2,
                "三": 3,
                "四": 4,
                "五": 5,
                "六": 6,
                "七": 7,
                "八": 8,
                "九": 9,
                "十": 10,
            }
            people = chinese_numbers[match[2]]
        data["peopleCount"] = people
        if data["maxPrice"] is not None and data["budgetScope"] is None:
            data["budgetScope"] = "total"
    match = re.search(rf"(?:至少|最低){amount}元", text)
    if match:
        data["minPrice"] = Decimal(match[1])
    for words, low, high in [
        (("不辣", "不要辣", "不吃辣", "不想吃辣"), 0, 0),
        (("微辣",), 1, 1),
        (("中辣",), 2, 2),
        (("重辣", "特辣"), 3, 4),
    ]:
        if contains_any(text, words):
            data.update(spicyLevelMin=low, spicyLevelMax=high)
            break
    if contains_any(text, ("别太辣", "不要太辣")):
        data["spicyLevelMax"] = 2
        minimum_spicy_level = data["spicyLevelMin"]
        if not minimum_spicy_level:
            minimum_spicy_level = 0
        data["spicyLevelMin"] = min(minimum_spicy_level, 2)
    if contains_any(text, ("想吃辣", "辣一点", "吃点辣")):
        data["spicyLevelMin"] = 1
        maximum_spicy_level = data["spicyLevelMax"]
        if not maximum_spicy_level:
            maximum_spicy_level = 4
        data["spicyLevelMax"] = max(maximum_spicy_level, 1)
    preferred = set(data["preferredTags"])
    excluded = set(data["excludedTags"])
    allergens = set(data["allergens"])
    if contains_any(text, ("不要甜", "不甜", "不吃甜", "不想吃甜")):
        data["sweetnessLevelMax"] = 0
        excluded.add("甜")
    for tag in TAGS:
        if tag not in text:
            continue
        is_excluded = False
        for prefix in ("不要", "不吃", "不想吃"):
            if prefix + tag in text:
                is_excluded = True
                break
        if is_excluded:
            preferred.discard(tag)
            excluded.add(tag)
        else:
            excluded.discard(tag)
            preferred.add(tag)
    if "优先辣" in text:
        preferred.discard("清淡")
    if "优先清淡" in text:
        data.update(spicyLevelMin=0, spicyLevelMax=0)
    for allergen in ALLERGENS:
        if allergen + "过敏" in text:
            allergens.add(allergen)
    if "麸质过敏" in text or "大麦过敏" in text:
        allergens.add("含麸质谷物")
    data.update(
        preferredTags=sorted(preferred), excludedTags=sorted(excluded), allergens=sorted(allergens)
    )
    return Preferences.model_validate(data)
