"""供意图判断和偏好提取使用的文本检查。"""


def contains_any(text: str, words) -> bool:
    """逐个检查关键词，找到一个就返回 True；全部未找到则返回 False。"""
    for word in words:
        if word in text:
            return True
    return False
