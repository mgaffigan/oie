import { useState } from "react";
import logo from "../assets/main_logo.svg";
import css from "./MenuBar.module.scss";
import { Link } from "react-router";
import dashboardIcon from "../assets/icons/application_view_detail.png";
import channelIcon from "../assets/icons/application_form.png";
import userIcon from "../assets/icons/user.png";
import usersIcon from "../assets/icons/group.png";
import settingsIcon from "../assets/icons/wrench.png";
import alertsIcon from "../assets/icons/error.png";
import eventsIcon from "../assets/icons/table.png";
import extensionsIcon from "../assets/icons/plugin.png";
import { useSession, logout } from "../services/Session";

export function CommandGroup(params: {
    title: string,
    children: React.ReactNode
}) {
    return <div className={css.commandGroup} title={params.title}>
        <h3>{params.title}</h3>
        <ul>
            {params.children}
        </ul>
    </div>;
}

export function CommandLink(params: {
    title: string,
    href: string,
    icon?: string,
}) {
    return <li>
        <Link to={params.href} className={css.commandLink}>
            {params.icon && <img src={params.icon} alt="" aria-hidden="true" className={css.icon} />}
            {params.title}
        </Link>
    </li>;
}

export function CommandButton(params: {
    title: string,
    onClick: () => void,
    icon?: string,
}) {
    return <li>
        <button type="button" className={`btn btn-link ${css.commandButton}`} onClick={params.onClick}>
            {params.icon && <img src={params.icon} alt="" aria-hidden="true" className={css.icon} />}
            {params.title}
        </button>
    </li>;
}

function UserPlate() {
    const sess = useSession();

    return <div className={css.userPlate}>
        <div className="btn-group dropup">
            <button type="button" title="User options" className="btn dropdown-toggle" data-bs-toggle="dropdown" aria-expanded="false">
                <img src={userIcon} alt="" aria-hidden="true" className={css.userIcon} />
                {sess.user.firstName} {sess.user.lastName} ({sess.user.username})
            </button>
            <ul className="dropdown-menu">
                <li><button type="button" className="dropdown-item" onClick={logout}>Logout</button></li>
            </ul>
        </div>
    </div>;
}

export function MenuBar(params: { commands?: React.ReactNode }) {
    const [isCollapsed, setIsCollapsed] = useState(true);

    return <header className={`${css.menubar} ${isCollapsed ? css.collapsed : ''}`}>
        <div className={css.logoPlate}>
            <img src={logo} alt="Open Integration Engine Logo" className={css.logo} />
            <button type="button" className={css.collapseButton} onClick={() => setIsCollapsed(!isCollapsed)}>
                <div /><div /><div />
            </button>
        </div>
        <CommandGroup title="Engine">
            <CommandLink title="Dashboard" href="/" icon={dashboardIcon} />
            <CommandLink title="Channels" href="/" icon={channelIcon} />
            <CommandLink title="Users" href="/" icon={usersIcon} />
            <CommandLink title="Settings" href="/" icon={settingsIcon} />
            <CommandLink title="Alerts" href="/" icon={alertsIcon} />
            <CommandLink title="Events" href="/" icon={eventsIcon} />
            <CommandLink title="Extensions" href="/" icon={extensionsIcon} />
        </CommandGroup>
        {params.commands}
        <UserPlate />
    </header>;
}
