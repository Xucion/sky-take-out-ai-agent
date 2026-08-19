import Cookies from 'js-cookie';

// App
const sidebarStatusKey = 'sidebar_status';
export const getSidebarStatus = () => Cookies.get(sidebarStatusKey);
export const setSidebarStatus = (sidebarStatus: string) => Cookies.set(sidebarStatusKey, sidebarStatus);

// User
const storeId = 'storeId';
export const getStoreId = () => Cookies.get(storeId);
export const setStoreId = (id: string) => Cookies.set(storeId, id);
export const removeStoreId = () => Cookies.remove(storeId);

// User
const tokenKey = 'token';
export const getToken = () => Cookies.get(tokenKey);
export const setToken = (token: string) => Cookies.set(tokenKey, token);
export const removeToken = () => Cookies.remove(tokenKey);

// userInfo

const userInfoKey = 'userInfo';
const legacyUserInfoKey = 'user_info';
export const getUserInfo = () => {
  const value = Cookies.get(userInfoKey) || Cookies.get(legacyUserInfoKey);
  if (!value) return null;

  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
};
export const setUserInfo = (userInfo: object) => {
  Cookies.set(userInfoKey, JSON.stringify(userInfo));
  Cookies.remove(legacyUserInfoKey);
};
export const removeUserInfo = () => {
  Cookies.remove(userInfoKey);
  Cookies.remove(legacyUserInfoKey);
};

// printinfo

const printKey = 'print';
export const getPrint = () => Cookies.get(printKey);
export const setPrint = (useInfor: Object) => Cookies.set(printKey, useInfor);
export const removePrint = () => Cookies.remove(printKey);

// 获取消息
const newData = 'new';
export const getNewData = () => Cookies.get(newData);
export const setNewData = (val: Object) => Cookies.set(newData, val);
