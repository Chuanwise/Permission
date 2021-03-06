package cn.chuanwise.xiaoming.permission.interactors;

import cn.chuanwise.toolkit.container.Container;
import cn.chuanwise.util.CollectionUtil;
import cn.chuanwise.util.MapUtil;
import cn.chuanwise.xiaoming.annotation.Filter;
import cn.chuanwise.xiaoming.annotation.FilterParameter;
import cn.chuanwise.xiaoming.annotation.Required;
import cn.chuanwise.xiaoming.interactor.SimpleInteractors;
import cn.chuanwise.xiaoming.permission.Permission;
import cn.chuanwise.xiaoming.permission.PermissionPlugin;
import cn.chuanwise.xiaoming.permission.PermissionSystem;
import cn.chuanwise.xiaoming.permission.configuration.PluginConfiguration;
import cn.chuanwise.xiaoming.permission.permission.Authorizer;
import cn.chuanwise.xiaoming.permission.permission.Role;
import cn.chuanwise.xiaoming.permission.util.ChooseUtil;
import cn.chuanwise.xiaoming.permission.util.Words;
import cn.chuanwise.xiaoming.plugin.Plugin;
import cn.chuanwise.xiaoming.user.GroupXiaomingUser;
import cn.chuanwise.xiaoming.user.XiaomingUser;
import cn.chuanwise.xiaoming.util.AtUtil;
import cn.chuanwise.xiaoming.util.MiraiCodeUtil;

import java.util.*;

public class PermissionInteractors
        extends SimpleInteractors<PermissionPlugin> {
    private PermissionSystem permissionSystem;
    private PluginConfiguration configuration;

    @Override
    public void onRegister() {
        permissionSystem = plugin.getPermissionSystem();
        configuration = plugin.getConfiguration();

        xiaomingBot.getInteractorManager().registerParameterParser(Authorizer.class, context -> {
            final String inputValue = context.getInputValue();
            final Optional<Long> optionalCode = AtUtil.parseAt(inputValue);
            if (optionalCode.isPresent()) {
                final long accountCode = optionalCode.get();
                return Container.of(permissionSystem.createAuthorizer(accountCode));
            }

            return Container.ofOptional(ChooseUtil.chooseAccount(context.getUser(), inputValue));
        }, true, plugin);

        xiaomingBot.getInteractorManager().registerParameterParser(Permission.class, context -> {
            final Permission permission;
            final String inputValue = MiraiCodeUtil.contentToString(context.getInputValue());
            try {
                permission = Permission.compile(MiraiCodeUtil.contentToString(inputValue));
            } catch (Exception exception) {
                context.getUser().sendError("???????????????" + inputValue + "??????" + exception.getMessage());
                return null;
            }
            return Container.of(permission);
        }, true, plugin);
    }

    @Filter(Words.RESET + Words.ACCOUNT + Words.PERMISSION + " {qq}")
    @Filter(Words.RESET + Words.USER + Words.PERMISSION + " {qq}")
    @Required("permission.admin.account.reset")
    public void resetAccountPermission(XiaomingUser user, @FilterParameter("qq") long qq) {
        final String aliasAndCode = xiaomingBot.getAccountManager().getAliasAndCode(qq);
        if (permissionSystem.removeAuthorizer(qq)) {
            user.sendMessage("?????????????????????" + aliasAndCode + "??????????????????");
        } else {
            user.sendMessage("?????????" + aliasAndCode + "????????????????????????????????????????????????");
        }
    }

    @Filter(Words.ACCOUNT + Words.DEFAULT + Words.ROLE + " {qq}")
    @Filter(Words.USER + Words.DEFAULT + Words.ROLE + " {qq}")
    @Required("permission.admin.account.defaultRoles")
    public void listAccountDefaultRoles(XiaomingUser user, @FilterParameter("qq") long qq) {
        final String aliasAndCode = xiaomingBot.getAccountManager().getAliasAndCode(qq);
        final Optional<Authorizer> authorizer = permissionSystem.getAuthorizer(qq);

        user.sendMessage("????????????????????????\n" +
                CollectionUtil.toIndexString(permissionSystem.getAccountDefaultRoles(qq), Role::getSimpleDescription) +
                authorizer.map(x -> "\n??????????????????????????????").orElse(""));
    }

    /** ???????????? */
    @Filter(Words.SET + Words.DEFAULT + Words.ROLE + " {??????}")
    @Filter(Words.SET + Words.GLOBAL + Words.DEFAULT + Words.ROLE + " {??????}")
    @Required("permission.admin.role.globalDefault.set")
    public void setGlobalDefaultRole(XiaomingUser user, @FilterParameter("??????") Role role) {
        configuration.setGlobalDefaultRoleCode(role.getRoleCode());
        user.sendMessage("??????????????????" + role.getSimpleDescription() + "?????????????????????");
    }

    @Filter(Words.SET + Words.GROUP + Words.DEFAULT + Words.ROLE + " {?????????} {??????}")
    @Required("permission.admin.role.groupDefault.set")
    public void setGroupDefaultRole(XiaomingUser user,
                                    @FilterParameter("?????????") String groupTag,
                                    @FilterParameter("??????") Role role) {
        final Map<String, Long> defaultRoleCodes = permissionSystem.getConfiguration().getGroupDefaultRoleCodes();
        defaultRoleCodes.put(groupTag, role.getRoleCode());
        permissionSystem.readyToSave();

        user.sendMessage("??????????????? %" + groupTag + " ????????????????????????????????? " + role.getSimpleDescription());
    }

    @Filter(Words.REMOVE + Words.GROUP + Words.DEFAULT + Words.ROLE + " {?????????}")
    @Required("permission.admin.role.groupDefault.remove")
    public void removeGroupDefaultRole(XiaomingUser user,
                                       @FilterParameter("?????????") String groupTag) {
        final Map<String, Long> defaultRoleCodes = permissionSystem.getConfiguration().getGroupDefaultRoleCodes();
        defaultRoleCodes.remove(groupTag);
        permissionSystem.readyToSave();

        user.sendMessage("??????????????? %" + groupTag + " ????????????????????????????????????");
    }

    @Filter(Words.GROUP + Words.DEFAULT + Words.ROLE + " {?????????}")
    @Required("permission.admin.role.groupDefault.look")
    public void lookGroupDefaultRole(XiaomingUser user,
                                     @FilterParameter("?????????") String groupTag) {
        final Optional<Long> optionalCode = permissionSystem.getConfiguration().getNakedGroupDefaultRoleCode(groupTag);
        if (optionalCode.isPresent()) {
            final long roleCode = optionalCode.get();
            final Optional<Role> optionalRole = permissionSystem.getRole(roleCode);
            user.sendMessage("?????? %" + groupTag + " ????????????????????????????????? " +
                    optionalRole.map(Role::getSimpleDescription).orElse("#" + roleCode + "???"));
        } else {
            final long roleCode = permissionSystem.getConfiguration().getGlobalDefaultRoleCode();
            final Optional<Role> optionalRole = permissionSystem.getRole(roleCode);
            user.sendMessage("?????? %" + groupTag + " ???????????????????????????????????????????????? " +
                    optionalRole.map(Role::getSimpleDescription).orElse("#" + roleCode + "???"));
        }
    }

    @Filter(Words.SET + Words.THIS + Words.GROUP + Words.DEFAULT + Words.ROLE + " {??????}")
    @Required("permission.admin.role.groupDefault.set")
    public void setThisGroupDefaultRole(GroupXiaomingUser user,
                                        @FilterParameter("??????") Role role) {
        final String groupTag = user.getGroupCodeString();
        final Map<String, Long> defaultRoleCodes = permissionSystem.getConfiguration().getGroupDefaultRoleCodes();
        defaultRoleCodes.put(groupTag, role.getRoleCode());
        permissionSystem.readyToSave();

        user.sendMessage("???????????????????????????????????????????????? " + role.getSimpleDescription());
    }

    @Filter(Words.REMOVE + Words.THIS + Words.GROUP + Words.DEFAULT + Words.ROLE)
    @Required("permission.admin.role.groupDefault.remove")
    public void removeThisGroupDefaultRole(GroupXiaomingUser user) {
        final String groupTag = user.getGroupCodeString();
        final Map<String, Long> defaultRoleCodes = permissionSystem.getConfiguration().getGroupDefaultRoleCodes();
        defaultRoleCodes.remove(groupTag);
        permissionSystem.readyToSave();

        user.sendMessage("???????????????????????????????????????????????????");
    }

    @Filter(Words.THIS + Words.GROUP + Words.DEFAULT + Words.ROLE)
    @Required("permission.admin.role.groupDefault.look")
    public void lookThisGroupDefaultRole(GroupXiaomingUser user) {
        final String groupTag = user.getGroupCodeString();
        final Optional<Long> optionalCode = permissionSystem.getConfiguration().getNakedGroupDefaultRoleCode(groupTag);
        if (optionalCode.isPresent()) {
            final long roleCode = optionalCode.get();
            final Optional<Role> optionalRole = permissionSystem.getRole(roleCode);
            user.sendMessage("???????????????????????????????????? " +
                    optionalRole.map(Role::getSimpleDescription).orElse("#" + roleCode + "???"));
        } else {
            final long roleCode = permissionSystem.getConfiguration().getGlobalDefaultRoleCode();
            final Optional<Role> optionalRole = permissionSystem.getRole(roleCode);
            user.sendMessage("??????????????????????????????????????????????????? " +
                    optionalRole.map(Role::getSimpleDescription).orElse("#" + roleCode + "???"));
        }
    }

    @Filter(Words.FLUSH + Words.PERMISSION)
    @Required("permission.admin.permission.flush")
    public void flush(XiaomingUser user) {
        permissionSystem.flush();
        user.sendMessage("????????????????????????");
    }

    @Filter(Words.PERMISSION + Words.LIST)
    @Required("permission.admin.permission.list")
    public void listPermission(XiaomingUser user) {
        final Map<Plugin, List<Permission>> permissions = new HashMap<>();
        permissionSystem.getPermissionHandlers().forEach(handler -> {
            MapUtil.getOrPutSupply(permissions, handler.getPlugin(), ArrayList::new).add(handler.getPermission());
        });
        permissions.values().forEach(list -> Collections.sort(list, Comparator.comparing(Permission::toString)));

        if (permissions.isEmpty()) {
            user.sendError("??????????????????????????????");
        } else {
            user.sendMessage("????????????????????????????????????\n" +
                    CollectionUtil.toIndexString(permissions.entrySet(), entry -> {
                        final Plugin plugin = entry.getKey();
                        final List<Permission> requiredPermissions = entry.getValue();

                        return Plugin.getChineseName(plugin) + "???" + requiredPermissions.size() + "?????????\n" +
                                CollectionUtil.toString(requiredPermissions, "\n");
                    }));
        }
    }

    @Filter(Words.COMPILE + Words.PERMISSION + " {r:??????}")
    @Filter(Words.COMPILE + Words.PERMISSION + " {r:??????}")
    @Required("permission.admin.compile")
    public void compilePermission(XiaomingUser user, @FilterParameter("??????") Permission permission) {
        user.sendMessage("???????????????" + permission);
    }

    @Filter(Words.TEST + Words.PERMISSION + " {??????1} {r:??????2}")
    @Required("permission.admin.test")
    public void testPermission(XiaomingUser user,
                               @FilterParameter("??????1") Permission left,
                               @FilterParameter("??????2") Permission right) {
        user.sendMessage(left + " => " + right + "???" + left.acceptable(right).toChinese());
    }

    @Filter(Words.REGISTER + Words.PERMISSION + Words.SERVICE)
    @Required("permission.admin.register")
    public void registerPermissionService(XiaomingUser user) {
        if (plugin.registerPermissionService()) {
            user.sendMessage("????????????????????? Permission ???????????????????????????");
        } else {
            user.sendMessage("????????????");
        }
    }
}