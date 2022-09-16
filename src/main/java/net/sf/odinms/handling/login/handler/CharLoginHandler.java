/*
 This file is part of the OdinMS Maple Story Server
 Copyright (C) 2008 ~ 2010 Patrick Huy <patrick.huy@frz.cc> 
 Matthias Butz <matze@odinms.de>
 Jan Christian Meyer <vimes@odinms.de>

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License version 3
 as published by the Free Software Foundation. You may not use, modify
 or distribute this program under any other version of the
 GNU Affero General Public License.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.odinms.handling.login.handler;

import java.util.List;
import java.util.Calendar;

import net.sf.odinms.client.inventory.IItem;
import net.sf.odinms.client.inventory.Item;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleCharacterUtil;
import net.sf.odinms.client.inventory.MapleInventory;
import net.sf.odinms.client.inventory.MapleInventoryType;
import net.sf.odinms.handling.channel.ChannelServer;
import net.sf.odinms.handling.login.LoginInformationProvider;
import net.sf.odinms.handling.login.LoginServer;
import net.sf.odinms.handling.login.LoginWorker;
import net.sf.odinms.server.MapleItemInformationProvider;
import net.sf.odinms.server.ServerProperties;
import net.sf.odinms.server.quest.MapleQuest;
import net.sf.odinms.tools.FileoutputUtil;
import net.sf.odinms.tools.KoreanDateUtil;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.StringUtil;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;
import net.sf.odinms.tools.packet.LoginPacket;

public class CharLoginHandler {

    private static final boolean loginFailCount(final MapleClient c) {
        c.loginAttempt++;
        if (c.loginAttempt > 5) {
            return true;
        }
        return false;
    }

    public static final void Welcome(final MapleClient c) {
    }

    public static final void login(final SeekableLittleEndianAccessor slea, final MapleClient c) {
        final String login = slea.readMapleAsciiString();
        final String pwd = slea.readMapleAsciiString();

        c.setAccountName(login);

        int[] bytes = new int[6];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = slea.readByteAsInt();
        }
        StringBuilder sps = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            sps.append(StringUtil.getLeftPaddedStr(Integer.toHexString(bytes[i]).toUpperCase(), '0', 2));
            sps.append("-");
        }
        String macData = sps.toString();
        macData = macData.substring(0, macData.length() - 1);

        c.setMac(macData);

        final boolean ipBan = c.hasBannedIP();
        final boolean macBan = c.isBannedMac(macData);
        final boolean banned = ipBan || macBan;

        int loginok = 0;
        if (Boolean.parseBoolean(ServerProperties.getProperty("ZlhssMS.AutoRegister"))) {

            if (AutoRegister.autoRegister && !AutoRegister.getAccountExists(login) && (!banned)) {
                if (pwd.equalsIgnoreCase("disconnect") || pwd.equalsIgnoreCase("fixme")) {
                    c.getSession().write(MaplePacketCreator.serverNotice(1, "This password is invalid."));
                    c.getSession().write(LoginPacket.getLoginFailed(1)); //Shows no message, used for unstuck the login button
                    return;
                }
                AutoRegister.createAccount(login, pwd, c.getSession().getRemoteAddress().toString(), macData);
                if (AutoRegister.success && AutoRegister.mac) {
                    c.getSession().write(MaplePacketCreator.serverNotice(1, "账号创建成功,请尝试重新登录!\r\n拒绝一切第三方辅助程序\r\n提倡手动从我做起-开挂死爹死妈\r\n！"));
                } else if (!AutoRegister.mac) {
                    c.getSession().write(MaplePacketCreator.serverNotice(1, "账号创建失败，你已经注册过账号，一个机器码只能注册一个账号"));
                }
                AutoRegister.success = true;
                AutoRegister.mac = true;
                c.getSession().write(LoginPacket.getLoginFailed(1)); //Shows no message, used for unstuck the login button
                return;
            }
        }

        // loginok = c.fblogin(login, pwd, ipBan || macBan);
        loginok = c.login(login, pwd, ipBan || macBan);

        final Calendar tempbannedTill = c.getTempBanCalendar();
        if (loginok == 0 && (ipBan || macBan) && !c.isGm()) {
            loginok = 3;
            if (macBan) {
                // this is only an ipban o.O" - maybe we should refactor this a bit so it's more readable
                //    MapleCharacter.ban(c.getSession().getRemoteAddress().toString().split(":")[0], "Enforcing account ban, account " + login, false, 4, false);
            }
        }
        if (loginok != 0) {
            if (!loginFailCount(c)) {
                c.getSession().write(LoginPacket.getLoginFailed(loginok));
            }
        } else if (tempbannedTill.getTimeInMillis() != 0) {
            if (!loginFailCount(c)) {
                c.getSession().write(LoginPacket.getTempBan(KoreanDateUtil.getTempBanTimestamp(tempbannedTill.getTimeInMillis()), c.getBanReason()));
            }
        } else {
            FileoutputUtil.logToFile("日志/logs/ACPW.txt", "ACC: " + login + " PW: " + pwd + " MAC : " + macData + " IP: " + c.getSession().getRemoteAddress().toString() + "\r\n");
            c.updateMacs();
            c.loginAttempt = 0;
            LoginWorker.registerClient(c);

        }
    }


    /*
     * public static final void login(final SeekableLittleEndianAccessor slea,
     * final MapleClient c) { final String login = slea.readMapleAsciiString();
     * final String pwd = slea.readMapleAsciiString();
     *
     * c.setAccountName(login); final boolean ipBan = c.hasBannedIP(); final
     * boolean macBan = c.hasBannedMac();
     *
     * int loginok = c.login(login, pwd, ipBan || macBan); final Calendar
     * tempbannedTill = c.getTempBanCalendar();
     *
     * if (loginok == 0 && (ipBan || macBan) && !c.isGm()) { loginok = 3; if
     * (macBan) { // this is only an ipban o.O" - maybe we should refactor this
     * a bit so it's more readable
     * MapleCharacter.ban(c.getSession().getRemoteAddress().toString().split(":")[0],
     * "Enforcing account ban, account " + login, false, 4, false); } } if
     * (loginok != 0) { if (!loginFailCount(c)) {
     * c.getSession().write(LoginPacket.getLoginFailed(loginok)); } } else if
     * (tempbannedTill.getTimeInMillis() != 0) { if (!loginFailCount(c)) {
     * c.getSession().write(LoginPacket.getTempBan(KoreanDateUtil.getTempBanTimestamp(tempbannedTill.getTimeInMillis()),
     * c.getBanReason())); } } else { c.loginAttempt = 0;
     * LoginWorker.registerClient(c); } }
     */
    public static final void SetGenderRequest(final SeekableLittleEndianAccessor slea, final MapleClient c) {
        byte gender = slea.readByte();
        String username = slea.readMapleAsciiString();
        // String password = slea.readMapleAsciiString();
        if (c.getAccountName().equals(username)) {
            c.setGender(gender);
            //  c.setSecondPassword(password);
            c.updateSecondPassword();
            c.updateGender();
            c.getSession().write(LoginPacket.getGenderChanged(c));
            c.getSession().write(MaplePacketCreator.licenseRequest());
            c.updateLoginState(MapleClient.LOGIN_NOTLOGGEDIN, c.getSessionIPAddress());
        } else {
            c.getSession().close();
        }
    }

    public static final void ServerListRequest(final MapleClient c) {
        c.getSession().write(LoginPacket.getServerList(0, LoginServer.getServerName(), LoginServer.getLoad()));
        //c.getSession().write(MaplePacketCreator.getServerList(1, "Scania", LoginServer.getInstance().getChannels(), 1200));
        //c.getSession().write(MaplePacketCreator.getServerList(2, "Scania", LoginServer.getInstance().getChannels(), 1200));
        //c.getSession().write(MaplePacketCreator.getServerList(3, "Scania", LoginServer.getInstance().getChannels(), 1200));
        c.getSession().write(LoginPacket.getEndOfServerList());
    }

    public static final void ServerStatusRequest(final MapleClient c) {
        // 0 = Select world normally
        // 1 = "Since there are many users, you may encounter some..."
        // 2 = "The concurrent users in this world have reached the max"
        final int numPlayer = LoginServer.getUsersOn();
        final int userLimit = LoginServer.getUserLimit();
        if (numPlayer >= userLimit) {
            c.getSession().write(LoginPacket.getServerStatus(2));
        } else if (numPlayer * 2 >= userLimit) {
            c.getSession().write(LoginPacket.getServerStatus(1));
        } else {
            c.getSession().write(LoginPacket.getServerStatus(0));
        }
    }

    public static final void LicenseRequest(final SeekableLittleEndianAccessor slea, final MapleClient c) {
        if (slea.readByte() == 1) {
            c.getSession().write(MaplePacketCreator.licenseResult());
            c.updateLoginState(0);
        } else {
            c.getSession().close();
        }
    }

    public static final void CharlistRequest(final SeekableLittleEndianAccessor slea, final MapleClient c) {
        // slea.readByte();
        final int server = slea.readByte();
        final int channel = slea.readByte() + 1;
        slea.readInt();

        c.setWorld(server);
        //System.out.println("Client " + c.getSession().getRemoteAddress().toString().split(":")[0] + " is connecting to server " + server + " channel " + channel + "");
        c.setChannel(channel);

        final List<MapleCharacter> chars = c.loadCharacters(server);
        if (chars != null) {
            c.getSession().write(LoginPacket.getCharList(c.getSecondPassword() != null, chars, c.getCharacterSlots()));
        } else {
            c.getSession().close();
        }
    }

    public static final void CheckCharName(final String name, final MapleClient c) {
        c.getSession().write(LoginPacket.charNameResponse(name, !MapleCharacterUtil.canCreateChar(name) || LoginInformationProvider.getInstance().isForbiddenName(name)));
    }

    //创建角色处理函数
    public static final void CreateChar(final SeekableLittleEndianAccessor slea, final MapleClient c) {
        final String name = slea.readMapleAsciiString();
        /*if(name == "ZlhssMS"){
            c.getSession().write(MaplePacketCreator.serverNotice(1, "此名称无法创建"));
            return;
        }*/
        final int JobType = slea.readInt(); // 1 = 冒险家, 0 = 骑士团, 2 = 战神
        boolean 冒险家 = Boolean.parseBoolean(ServerProperties.getProperty("ZlhssMS.冒险家", "false"));
        boolean 骑士团 = Boolean.parseBoolean(ServerProperties.getProperty("ZlhssMS.骑士团", "false"));
        boolean 战神 = Boolean.parseBoolean(ServerProperties.getProperty("ZlhssMS.战神", "false"));
        if (!骑士团 && JobType == 0) {
            c.getSession().write(MaplePacketCreator.serverNotice(1, "骑士团职业未开放！"));
            return;//直接return可能会导致客户端收不到消息，假死状态,需重新登录方可
        } else if (!冒险家 && JobType == 1) {
            c.getSession().write(MaplePacketCreator.serverNotice(1, "冒险家职业未开放！"));
            return;//直接return可能会导致客户端收不到消息，假死状态,需重新登录方可
        } else if (!战神 && JobType == 2) {
            c.getSession().write(MaplePacketCreator.serverNotice(1, "战神职业未开放！"));
            return;//直接return可能会导致客户端收不到消息，假死状态,需重新登录方可
        }
        final short db = 0; //whether dual blade = 1 or 冒险家 = 0
        final int face = slea.readInt();//脸型
        final int hair = slea.readInt();//发型
        final int hairColor = 0;//头发的颜色
        final byte skinColor = 0;//皮肤颜色
        final int top = slea.readInt();//上衣或者套服
        final int bottom = slea.readInt();//裤子
        final int shoes = slea.readInt();//鞋子
        final int weapon = slea.readInt();//武器
        final byte gender = c.getGender();//性别
        switch (gender) {
            case 0:
                //如果是男的
                //20100 - 男脸1 - (无描述)
                //20401 - 男脸2 - (无描述)
                //20402 - 男脸3 - (无描述)
                if (face != 20100 && face != 20401 && face != 20402) {
                    c.getSession().write(MaplePacketCreator.serverNotice(1, "创建失败，脸型数据异常(男)！"));
                    return;//如果创建的角色不是如上脸型，那么返回.即创建无效
                }   //30000 - 黑色短中分 - (无描述)
                //30030 - 黑色短光头 - (无描述)
                //30027 - 褐色酷型侧分 - (无描述)
                if (hair != 30030 && hair != 30027 && hair != 30000) {
                    c.getSession().write(MaplePacketCreator.serverNotice(1, "创建失败，发型数据异常(男)！"));
                    return;//如果创建的角色不是如上发型，那么返回.即创建无效
                }   //1040002 - 白棉T恤 - (无描述)
                //1040006 - 白色背心 - (无描述)
                //1040010 - 灰棉T恤 - (无描述)
                //1042167 - 朴素的武士服(男) - (无描述)
                if (top != 1040002 && top != 1040006 && top != 1040010 && top != 1042167) {
                    c.getSession().write(MaplePacketCreator.serverNotice(1, "创建失败，上衣数据异常(男)！"));
                    return;//如果创建的角色不是如上上衣，那么返回.即创建无效
                }   //1060002 - 蓝牛仔短裤 - (无描述)
                //1060006 - 黄牛仔短裤 - (无描述)
                //1062115 - 朴素的武士裤(男) - (无描述)
                if (bottom != 1060002 && bottom != 1060006 && bottom != 1062115) {
                    c.getSession().write(MaplePacketCreator.serverNotice(1, "创建失败，裤裙数据异常(男)！"));
                    return;//如果创建的角色不是如上上衣，那么返回.即创建无效
                }
                break;
            case 1:
                //如果是女的
                //21002
                //21700
                //21201
                if (face != 21002 && face != 21700 && face != 21201) {
                    c.getSession().write(MaplePacketCreator.serverNotice(1, "创建失败，脸型数据异常(女)！"));
                    return;
                }   //31002 - 橙色羊角辫 - (无描述)
                //31047 - 褐色边分翘发 - (无描述)
                //31057 - 褐色直发 - (无描述)
                if (hair != 31002 && hair != 31047 && hair != 31057) {
                    c.getSession().write(MaplePacketCreator.serverNotice(1, "创建失败，发型数据异常(女)！"));
                    return;
                }   //1041002 - 白色无袖短T - (无描述)
                //1041006 - 黄色短T - (无描述)
                //1041010 - 绿色短T - (无描述)
                //1041011 - 红线无袖衫 - (无描述)
                //1042167 - 朴素的武士服(女) - (无描述)
                if (top != 1041002 && top != 1041006 && top != 1041010 && top != 1041011 && top != 1042167) {
                    c.getSession().write(MaplePacketCreator.serverNotice(1, "创建失败，上衣数据异常(女)！"));
                    return;
                }   //1061002 - 红超短裙 - (无描述)
                //1061008 - 蓝超短裙 - (无描述)
                //1062115 - 朴素的武士裤(女) - (无描述)
                if (bottom != 1061002 && bottom != 1061008 && bottom != 1062115) {
                    c.getSession().write(MaplePacketCreator.serverNotice(1, "创建失败，裤裙数据异常(女)！"));
                    return;
                }
                break;
            default:
                return;//女的创建失败
        }
        //1072001 - 红橡胶长靴 - (无描述)
        //1072005 - 皮制凉鞋 - (无描述)
        //1072037 - 黄橡胶长靴 - (无描述)
        //1072038 - 蓝橡胶长靴 - (无描述)
        //1072383 - 朴素的武士靴(男女通用) - (无描述)
        if (shoes != 1072001 && shoes != 1072005 && shoes != 1072037 && shoes != 1072038 && shoes != 1072383) {
            c.getSession().write(MaplePacketCreator.serverNotice(1, "创建失败，鞋子数据异常！"));
            return;
        }
        //1302000 - 剑 - (无描述)
        //1322005 - 棍棒 - (无描述)
        //1312004 - 小斧 - (无描述)
        //1442079 - 基本矛 - (无描述)
        if (weapon != 1302000 && weapon != 1322005 && weapon != 1312004 && weapon != 1442079) {
            c.getSession().write(MaplePacketCreator.serverNotice(1, "创建失败，武器数据异常！"));
            return;
        }
        //下面开始执行写入数据库存储
        MapleCharacter newchar = MapleCharacter.getDefault(c, JobType);
        newchar.setWorld((byte) c.getWorld());//将角色数据存储到进入的大区服务器内 多区时，此处重要
        newchar.setFace(face);//设置角色的脸型到数据库
        newchar.setHair(hair + hairColor);//设置角色的发型 + 头发颜色到数据库
        newchar.setSkinColor(skinColor);//设置角色的皮肤颜色到数据库
        newchar.setGender(gender);//设置角色的性别到数据库
        newchar.setName(name);//设置角色的名称到数据库

        MapleInventory equip = newchar.getInventory(MapleInventoryType.EQUIPPED);//装备 变量
        final MapleItemInformationProvider li = MapleItemInformationProvider.getInstance();//装备的类型

        IItem item = li.getEquipById(top);//是上衣或套服
        item.setPosition((byte) -5);//设置角色的上衣数据，穿戴在角色装备栏坐标，-5为上衣栏
        equip.addFromDB(item);//存储装备信息

        item = li.getEquipById(bottom);//是裤裙
        item.setPosition((byte) -6);//设置角色的裤裙数据，穿戴在角色装备栏坐标，-6为裤裙栏
        equip.addFromDB(item);//存储装备信息

        item = li.getEquipById(shoes);//是鞋子
        item.setPosition((byte) -7);//设置角色的鞋子数据，穿戴在角色装备栏坐标，-7为鞋子栏
        equip.addFromDB(item);//存储装备信息

        item = li.getEquipById(weapon);//是武器
        item.setPosition((byte) -11);//设置角色的武器数据，穿戴在角色装备栏坐标，-11为武器栏
        equip.addFromDB(item);//存储装备信息

        //blue/red pots
        switch (JobType) {
            case 0: // 骑士团
                newchar.setQuestAdd(MapleQuest.getInstance(20022), (byte) 1, "1");//创建骑士团自动给予任务，否则骑士团角色无法接取系统任务
                newchar.setQuestAdd(MapleQuest.getInstance(20010), (byte) 1, null); //
                newchar.setQuestAdd(MapleQuest.getInstance(20000), (byte) 1, null); //
                newchar.setQuestAdd(MapleQuest.getInstance(20015), (byte) 1, null); //
                newchar.setQuestAdd(MapleQuest.getInstance(20020), (byte) 1, null); //
                //获取背包其他栏，往其他栏(ETC)写入道具数据
                newchar.getInventory(MapleInventoryType.ETC).addItem(new Item(4161047, (byte) 0, (short) 1, (byte) 0));// - 初心者指南 - 是为初心者准备的指南。 \n#c双击道具可以翻开书本。#
                break;
            case 1: // 冒险家
                //获取背包其他栏，往其他栏(ETC)写入道具数据
                newchar.getInventory(MapleInventoryType.ETC).addItem(new Item(4161001, (byte) 0, (short) 1, (byte) 0));// - 新手指南 - 注入神秘星星精神的石头。拥有与护身符相同的效果，角色死亡时不会掉落经验值的功效。\n于有效期限可使用一次。
                break;
            case 2: // 战神
                //获取背包其他栏，往其他栏(ETC)写入道具数据
                newchar.getInventory(MapleInventoryType.ETC).addItem(new Item(4161048, (byte) 0, (short) 1, (byte) 0));//- 战童指南 - 战童专用指南。\n#c双击道具，可以把书打开。#
                break;
        }
        newchar.getInventory(MapleInventoryType.USE).addItem(new Item(2022336, (byte) 0, (short) 1, (byte) 0));//5530000//- 秘密箱子 - 新手礼包
        //获取玩家的名称 字符
        //从WZ的XML中  即 Etc.wz\ForbiddenName.img 中 获取限制字符
        //如果玩家的名称字符 不等于 从XML节点中获取到的限制字符.即名称符合标准
        if (MapleCharacterUtil.canCreateChar(name) && !LoginInformationProvider.getInstance().isForbiddenName(name)) {
            MapleCharacter.saveNewCharToDB(newchar, JobType, JobType == 1 && db == 0);//那么保存这个新创建角色的装备、头发、脸型、职业等数据到数据库进行存储
            c.getSession().write(LoginPacket.addNewCharEntry(newchar, true));//发送验证名称的封包，为true 通过
            c.createdChar(newchar.getId());//创建这个玩家的ID
        } else {
            c.getSession().write(LoginPacket.addNewCharEntry(newchar, false));//反之提示名称不可用 为false 名称不可用
        }
    }

    public static final void DeleteChar(final SeekableLittleEndianAccessor slea, final MapleClient c) {
        slea.readByte();
        String Secondpw_Client = null;
//        if (slea.readByte() > 0) { // Specific if user have second password or not
        Secondpw_Client = slea.readMapleAsciiString();
//        }
//        slea.readMapleAsciiString();
        final int Character_ID = slea.readInt();

        if (!c.login_Auth(Character_ID)) {
            c.getSession().write(LoginPacket.secondPwError((byte) 0x14));
            return; // Attempting to delete other character
        }
        byte state = 0;

        if (c.getSecondPassword() != null) { // On the server, there's a second password
            if (Secondpw_Client == null) { // Client's hacking
                c.getSession().close();
                return;
            } else if (!c.CheckSecondPassword(Secondpw_Client)) { // Wrong Password
                //state = 12;
                state = 16;
            }
        }
        // TODO, implement 13 digit Asiasoft passport too.

        if (state == 0) {
            state = (byte) c.deleteCharacter(Character_ID);
        }
        c.getSession().write(LoginPacket.deleteCharResponse(Character_ID, state));
    }

    public static void Character_WithoutSecondPassword(final SeekableLittleEndianAccessor slea, final MapleClient c) {
//        slea.skip(1);
        /*
         * if (c.getLoginState() != 2) { return; }
         */
        final int charId = slea.readInt();
        if ((!c.isLoggedIn()) || (loginFailCount(c)) || (!c.login_Auth(charId))) {
            c.getSession().write(MaplePacketCreator.enableActions());
            return;
        }
        if ((ChannelServer.getInstance(c.getChannel()) == null) || (c.getWorld() != 0)) {
            c.getSession().close();
            return;
        }
        if (c.getIdleTask() != null) {
            c.getIdleTask().cancel(true);
        }
        String ip = c.getSessionIPAddress();
        LoginServer.putLoginAuth(charId, ip.substring(ip.indexOf('/') + 1, ip.length()), c.getTempIP(), c.getChannel());
        // c.updateLoginState(MapleClient.LOGIN_SERVER_TRANSITION, ip);
        /*
         * if (c.getLoginState() == 2) { c.updateLoginState(2, ip);
         * System.out.println("输出登录2"); } else {
         */
        c.getSession().write(MaplePacketCreator.getServerIP(Integer.parseInt(ChannelServer.getInstance(c.getChannel()).getIP().split(":")[1]), charId));

        /*
         * final String currentpw = c.getSecondPassword(); if (slea.available()
         * != 0) { if (currentpw != null) { // Hack c.getSession().close();
         * return; } final String setpassword = slea.readMapleAsciiString();
         *
         * if (setpassword.length() >= 4 && setpassword.length() <= 16) {
         * c.setSecondPassword(setpassword); c.updateSecondPassword();
         *
         * if (!c.login_Auth(charId)) { c.getSession().close(); return; } } else
         * { c.getSession().write(LoginPacket.secondPwError((byte) 0x14));
         * return; } } else if (loginFailCount(c) || currentpw != null ||
         * !c.login_Auth(charId)) { c.getSession().close(); return; }
         */
        //这句是我屏蔽的
        //   String ip = c.getSessionIPAddress();
        //   LoginServer.putLoginAuth(charId, ip.substring(ip.indexOf('/') + 1, ip.length()), c.getTempIP(), c.getChannel());
        //   c.updateLoginState(MapleClient.LOGIN_SERVER_TRANSITION, c.getSessionIPAddress());        
        //   System.out.println("··········A"+charId);
        //    System.out.println("··········C"+c.getSessionIPAddress());
        //    System.out.println("··········B"+ChannelServer.getInstance(c.getChannel()).getIP());
        //    c.getSession().write(MaplePacketCreator.getServerIP(Integer.parseInt(ChannelServer.getInstance(c.getChannel()).getIP().split(":")[1]), charId));
        //  c.getSession().write(MaplePacketCreator.getServerIP(0, charId));
    }

    public static final void Character_WithSecondPassword(final SeekableLittleEndianAccessor slea, final MapleClient c) {
        final String password = slea.readMapleAsciiString();
        final int charId = slea.readInt();

        if (loginFailCount(c) || c.getSecondPassword() == null || !c.login_Auth(charId)) { // This should not happen unless player is hacking
            c.getSession().close();
            return;
        }
        if (c.CheckSecondPassword(password)) {
            c.updateMacs(slea.readMapleAsciiString());
            if (c.getIdleTask() != null) {
                c.getIdleTask().cancel(true);
            }
            c.updateLoginState(MapleClient.LOGIN_SERVER_TRANSITION, c.getSessionIPAddress());
            c.getSession().write(MaplePacketCreator.getServerIP(Integer.parseInt(ChannelServer.getInstance(c.getChannel()).getIP().split(":")[1]), charId));
        } else {
            c.getSession().write(LoginPacket.secondPwError((byte) 0x14));
        }
    }
}
