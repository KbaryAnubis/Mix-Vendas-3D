package com.mixvendas3d.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mixvendas3d.app.data.local.dao.MachineDao
import com.mixvendas3d.app.data.local.dao.PersonDao
import com.mixvendas3d.app.data.local.dao.PreferenceDao
import com.mixvendas3d.app.data.local.dao.ProductDao
import com.mixvendas3d.app.data.local.dao.SaleDao
import com.mixvendas3d.app.data.local.entity.MachineBrandRateEntity
import com.mixvendas3d.app.data.local.entity.MachineEntity
import com.mixvendas3d.app.data.local.entity.MachineInstallmentRateEntity
import com.mixvendas3d.app.data.local.entity.PersonEntity
import com.mixvendas3d.app.data.local.entity.PreferenceEntity
import com.mixvendas3d.app.data.local.entity.ProductCategoryEntity
import com.mixvendas3d.app.data.local.entity.ProductEntity
import com.mixvendas3d.app.data.local.entity.ProductParticipantEntity
import com.mixvendas3d.app.data.local.entity.SaleEntity
import com.mixvendas3d.app.data.local.entity.SaleItemEntity
import com.mixvendas3d.app.data.local.entity.SaleItemParticipantEntity
import com.mixvendas3d.app.data.local.entity.SalePersonTotalEntity

@Database(
    entities = [
        PreferenceEntity::class,
        PersonEntity::class,
        ProductCategoryEntity::class,
        ProductEntity::class,
        ProductParticipantEntity::class,
        MachineEntity::class,
        MachineBrandRateEntity::class,
        MachineInstallmentRateEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        SaleItemParticipantEntity::class,
        SalePersonTotalEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun preferenceDao(): PreferenceDao
    abstract fun personDao(): PersonDao
    abstract fun productDao(): ProductDao
    abstract fun machineDao(): MachineDao
    abstract fun saleDao(): SaleDao

    companion object {
        const val DATABASE_NAME = "mix_vendas_3d.db"

        /**
         * Migração não destrutiva da v1 (Etapa 1: app_preferences) para v2
         * (Etapa 2B-1: people, products, product_participants), preservando todos os dados existentes.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `people` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `normalized_name` TEXT NOT NULL,
                        `color_index` INTEGER NOT NULL,
                        `created_at_epoch_ms` INTEGER NOT NULL,
                        `updated_at_epoch_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_people_normalized_name` ON `people` (`normalized_name`)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `products` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `price_cents` INTEGER NOT NULL,
                        `active` INTEGER NOT NULL,
                        `fee_responsibility_mode` TEXT NOT NULL,
                        `fee_owner_person_id` TEXT,
                        `created_at_epoch_ms` INTEGER NOT NULL,
                        `updated_at_epoch_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`fee_owner_person_id`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_products_fee_owner_person_id` ON `products` (`fee_owner_person_id`)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `product_participants` (
                        `product_id` TEXT NOT NULL,
                        `person_id` TEXT NOT NULL,
                        `amount_cents` INTEGER NOT NULL,
                        `sort_order` INTEGER NOT NULL,
                        PRIMARY KEY(`product_id`, `person_id`),
                        FOREIGN KEY(`product_id`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`person_id`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_product_participants_product_id` ON `product_participants` (`product_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_product_participants_person_id` ON `product_participants` (`person_id`)",
                )
            }
        }

        /**
         * Migração não destrutiva da v2 para v3 (Etapa 2B-2: machines, machine_brand_rates, machine_installment_rates),
         * preservando integralmente app_preferences, people, products e product_participants.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `machines` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `normalized_name` TEXT NOT NULL,
                        `debit_rate_bps` INTEGER NOT NULL,
                        `credit_enabled` INTEGER NOT NULL,
                        `credit_default_rate_bps` INTEGER NOT NULL,
                        `brand_rates_enabled` INTEGER NOT NULL,
                        `installments_enabled` INTEGER NOT NULL,
                        `created_at_epoch_ms` INTEGER NOT NULL,
                        `updated_at_epoch_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_machines_normalized_name` ON `machines` (`normalized_name`)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `machine_brand_rates` (
                        `machine_id` TEXT NOT NULL,
                        `brand_key` TEXT NOT NULL,
                        `rate_bps` INTEGER NOT NULL,
                        PRIMARY KEY(`machine_id`, `brand_key`),
                        FOREIGN KEY(`machine_id`) REFERENCES `machines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_machine_brand_rates_machine_id` ON `machine_brand_rates` (`machine_id`)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `machine_installment_rates` (
                        `machine_id` TEXT NOT NULL,
                        `installments` INTEGER NOT NULL,
                        `addon_bps` INTEGER NOT NULL,
                        PRIMARY KEY(`machine_id`, `installments`),
                        FOREIGN KEY(`machine_id`) REFERENCES `machines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_machine_installment_rates_machine_id` ON `machine_installment_rates` (`machine_id`)",
                )
            }
        }

        /**
         * Migração não destrutiva da v3 para v4 (Etapa 3B-1: sales, sale_items, sale_item_participants, sale_person_totals),
         * preservando integralmente app_preferences, people, products, product_participants,
         * machines, machine_brand_rates e machine_installment_rates.
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sales` (
                        `id` TEXT NOT NULL,
                        `sold_at_epoch_ms` INTEGER NOT NULL,
                        `updated_at_epoch_ms` INTEGER NOT NULL,
                        `payment_method` TEXT NOT NULL,
                        `original_subtotal_cents` INTEGER NOT NULL,
                        `discount_total_cents` INTEGER NOT NULL,
                        `final_amount_cents` INTEGER NOT NULL,
                        `fee_total_cents` INTEGER NOT NULL,
                        `net_total_cents` INTEGER NOT NULL,
                        `discount_mode` TEXT NOT NULL,
                        `discount_target_product_id` TEXT,
                        `discount_target_product_name` TEXT,
                        `machine_id` TEXT,
                        `machine_name` TEXT,
                        `machine_debit_rate_bps` INTEGER,
                        `machine_credit_default_rate_bps` INTEGER,
                        `machine_brand_rates_enabled` INTEGER,
                        `machine_installments_enabled` INTEGER,
                        `card_brand` TEXT,
                        `installments` INTEGER,
                        `automatic_rate_bps` INTEGER,
                        `manual_rate_override_bps` INTEGER,
                        `effective_fee_rate_bps` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sales_sold_at_epoch_ms` ON `sales` (`sold_at_epoch_ms`)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sale_items` (
                        `id` TEXT NOT NULL,
                        `sale_id` TEXT NOT NULL,
                        `sort_order` INTEGER NOT NULL,
                        `product_id` TEXT NOT NULL,
                        `product_name` TEXT NOT NULL,
                        `quantity` INTEGER NOT NULL,
                        `unit_price_original_cents` INTEGER NOT NULL,
                        `original_subtotal_cents` INTEGER NOT NULL,
                        `discount_amount_cents` INTEGER NOT NULL,
                        `final_amount_cents` INTEGER NOT NULL,
                        `applied_fee_rate_bps` INTEGER NOT NULL,
                        `fee_amount_cents` INTEGER NOT NULL,
                        `net_amount_cents` INTEGER NOT NULL,
                        `fee_responsibility_mode` TEXT NOT NULL,
                        `fee_owner_person_id` TEXT,
                        `fee_owner_person_name` TEXT,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`sale_id`) REFERENCES `sales`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sale_items_sale_id` ON `sale_items` (`sale_id`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_sale_items_sale_id_product_id` ON `sale_items` (`sale_id`, `product_id`)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sale_item_participants` (
                        `sale_item_id` TEXT NOT NULL,
                        `person_id` TEXT NOT NULL,
                        `sort_order` INTEGER NOT NULL,
                        `person_name` TEXT NOT NULL,
                        `person_color_index` INTEGER NOT NULL,
                        `original_unit_amount_cents` INTEGER NOT NULL,
                        `original_subtotal_cents` INTEGER NOT NULL,
                        `discount_amount_cents` INTEGER NOT NULL,
                        `gross_amount_cents` INTEGER NOT NULL,
                        `fee_amount_cents` INTEGER NOT NULL,
                        `net_amount_cents` INTEGER NOT NULL,
                        PRIMARY KEY(`sale_item_id`, `person_id`),
                        FOREIGN KEY(`sale_item_id`) REFERENCES `sale_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sale_item_participants_sale_item_id` ON `sale_item_participants` (`sale_item_id`)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sale_person_totals` (
                        `sale_id` TEXT NOT NULL,
                        `person_id` TEXT NOT NULL,
                        `sort_order` INTEGER NOT NULL,
                        `person_name` TEXT NOT NULL,
                        `person_color_index` INTEGER NOT NULL,
                        `gross_amount_cents` INTEGER NOT NULL,
                        `fee_amount_cents` INTEGER NOT NULL,
                        `net_amount_cents` INTEGER NOT NULL,
                        PRIMARY KEY(`sale_id`, `person_id`),
                        FOREIGN KEY(`sale_id`) REFERENCES `sales`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sale_person_totals_sale_id` ON `sale_person_totals` (`sale_id`)",
                )
            }
        }

        /**
         * Migração não destrutiva da v4 para v5 (Etapa V2-4B2A: max_installments em machines),
         * preservando integralmente todas as tabelas e dados existentes.
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `machines` ADD COLUMN `max_installments` INTEGER")
            }
        }

        /**
         * Migração não destrutiva da v5 para v6: categorias hierárquicas e classificações opcionais
         * nos produtos existentes (todos começam sem categoria).
         */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `product_categories` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `normalized_name` TEXT NOT NULL,
                        `parent_id` TEXT,
                        `created_at_epoch_ms` INTEGER NOT NULL,
                        `updated_at_epoch_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`parent_id`) REFERENCES `product_categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_product_categories_parent_id` ON `product_categories` (`parent_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_product_categories_parent_id_normalized_name` ON `product_categories` (`parent_id`, `normalized_name`)",
                )
                db.execSQL("ALTER TABLE `products` ADD COLUMN `category_id` TEXT")
                db.execSQL("ALTER TABLE `products` ADD COLUMN `subcategory_id` TEXT")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_products_category_id` ON `products` (`category_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_products_subcategory_id` ON `products` (`subcategory_id`)",
                )
            }
        }

        /**
         * Migração não destrutiva da v6 para v7: torna rate_bps anulável e adiciona debit_rate_bps
         * na tabela machine_brand_rates, preservando integralmente todas as linhas e taxas existentes.
         */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `machine_brand_rates_new` (
                        `machine_id` TEXT NOT NULL,
                        `brand_key` TEXT NOT NULL,
                        `rate_bps` INTEGER,
                        `debit_rate_bps` INTEGER,
                        PRIMARY KEY(`machine_id`, `brand_key`),
                        FOREIGN KEY(`machine_id`) REFERENCES `machines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `machine_brand_rates_new` (`machine_id`, `brand_key`, `rate_bps`, `debit_rate_bps`)
                    SELECT `machine_id`, `brand_key`, `rate_bps`, NULL FROM `machine_brand_rates`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `machine_brand_rates`")
                db.execSQL("ALTER TABLE `machine_brand_rates_new` RENAME TO `machine_brand_rates`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_machine_brand_rates_machine_id` ON `machine_brand_rates` (`machine_id`)",
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
