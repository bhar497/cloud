package com.cloud.upgrade.dao;

import com.cloud.utils.exception.CloudRuntimeException;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class Upgrade41130to41131 implements DbUpgrade {
    @Override
    public String[] getUpgradableVersionRange() {
        return new String[] { "4.11.3.0", "4.11.3.1" };
    }

    @Override
    public String getUpgradedVersion() {
        return "4.11.3.1";
    }

    @Override
    public boolean supportsRollingUpgrade() {
        return false;
    }

    @Override
    public InputStream[] getPrepareScripts() {
        return new InputStream[] {};
    }

    @Override
    public void performDataMigration(Connection conn) {
        try {
            PreparedStatement stmt = conn.prepareStatement("create table if not exists ip_reservation (\n" +
                "    id int auto_increment primary key,\n" +
                "    uuid varchar(40),\n" +
                "    start_ip varchar(15),\n" +
                "    end_ip varchar(15),\n" +
                "    network_id bigint unsigned,\n" +
                "    created datetime default NOW(),\n" +
                "    removed datetime,\n" +
                "    constraint fk_ip_reservation__networks_id foreign key (network_id) references networks (id),\n" +
                "    constraint uc_ip_reservation__uuid unique (uuid)\n" +
                ");");
            stmt.execute();
            stmt = conn.prepareStatement("alter table network_acl_item_cidrs add column is_source_cidr tinyint(1) DEFAULT NULL");
            stmt.executeUpdate();
            stmt = conn.prepareStatement("alter table s2s_customer_gateway modify remote_id varchar(80) NOT NULL");
            stmt.executeUpdate();

        } catch (final SQLException e) {
            throw new CloudRuntimeException("failed to create ip reservation table", e);
        }
    }

    @Override
    public InputStream[] getCleanupScripts() {
        return new InputStream[] {};
    }
}
