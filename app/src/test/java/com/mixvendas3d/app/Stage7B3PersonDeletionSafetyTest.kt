package com.mixvendas3d.app

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.local.dao.PersonDao
import com.mixvendas3d.app.data.local.dao.PersonDeletionResult
import com.mixvendas3d.app.data.local.entity.ProductEntity
import com.mixvendas3d.app.data.local.entity.ProductParticipantEntity
import com.mixvendas3d.app.data.repository.RoomCatalogRepository
import com.mixvendas3d.app.domain.calculation.CatalogValidationException
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Stage7B3PersonDeletionSafetyTest {

    private fun openInMemoryDb(
        context: Context,
        queryCallback: RoomDatabase.QueryCallback? = null,
    ): AppDatabase {
        val builder = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
        if (queryCallback != null) {
            builder.setQueryCallback(queryCallback, Runnable::run)
        }
        return builder.build()
    }

    @Test
    fun `unlinked person can be deleted while linked person and products remain intact`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = openInMemoryDb(context)
        try {
            val repo = RoomCatalogRepository(db.personDao(), db.productDao())
            val ana = repo.createPerson("Ana")
            val bruno = repo.createPerson("Bruno")

            val prod = repo.createProduct(
                name = "Suporte",
                price = MoneyCents(3_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(3_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )

            repo.deletePerson(bruno.id)

            assertNull(db.personDao().getById(bruno.id))
            assertNotNull(db.personDao().getById(ana.id))
            val products = repo.observeAllProducts().first()
            assertEquals(1, products.size)
            assertEquals(prod.id, products.first().id)
        } finally {
            db.close()
        }
    }

    @Test
    fun `deleting missing person throws expected CatalogValidationException message`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = openInMemoryDb(context)
        try {
            val repo = RoomCatalogRepository(db.personDao(), db.productDao())
            repo.createPerson("Ana")

            val error = runCatching { repo.deletePerson("missing-person-id") }.exceptionOrNull()
            assertTrue(error is CatalogValidationException)
            assertEquals("Pessoa não encontrada para exclusão.", error?.message)
            assertEquals(1, db.personDao().getAllPeople().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun `person linked as product participant cannot be deleted and preserves all data`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = openInMemoryDb(context)
        try {
            val repo = RoomCatalogRepository(db.personDao(), db.productDao())
            val ana = repo.createPerson("Ana")
            val bruno = repo.createPerson("Bruno")

            val product = repo.createProduct(
                name = "Vaso Geométrico",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(3_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(2_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.EQUAL,
                feeOwnerPersonId = null,
            )

            val error = runCatching { repo.deletePerson(ana.id) }.exceptionOrNull()
            assertTrue(error is CatalogValidationException)
            assertEquals(
                "Não é possível excluir \"Ana\" porque ela está vinculada a produto(s) existente(s).",
                error?.message,
            )

            // Todos os registros permanecem preservados
            val people = db.personDao().getAllPeople()
            assertEquals(2, people.size)
            assertEquals(ana.id, db.personDao().getById(ana.id)?.id)
            val savedProducts = repo.observeAllProducts().first()
            assertEquals(1, savedProducts.size)
            assertEquals(product.id, savedProducts.first().id)
            assertEquals(2, savedProducts.first().participants.size)
            assertEquals(3_000L, savedProducts.first().participants[0].amount.rawCents)
            assertEquals(2_000L, savedProducts.first().participants[1].amount.rawCents)
        } finally {
            db.close()
        }
    }

    @Test
    fun `person linked as fee owner cannot be deleted even when checked via fee_owner_person_id`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = openInMemoryDb(context)
        try {
            val repo = RoomCatalogRepository(db.personDao(), db.productDao())
            val owner = repo.createPerson("Carla")
            val participant = repo.createPerson("Diego")

            // Caso padrão INDIVIDUAL
            val prodIndividual = repo.createProduct(
                name = "Chaveiro",
                price = MoneyCents(2_500L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(owner.id, MoneyCents(1_500L)),
                    ProductParticipantDraft(participant.id, MoneyCents(1_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
                feeOwnerPersonId = owner.id,
            )

            val error = runCatching { repo.deletePerson(owner.id) }.exceptionOrNull()
            assertTrue(error is CatalogValidationException)
            assertEquals(
                "Não é possível excluir \"Carla\" porque ela está vinculada a produto(s) existente(s).",
                error?.message,
            )

            // Caso isolado somente em products.fee_owner_person_id (sem estar em product_participants)
            val ownerOnly = repo.createPerson("Eduarda")
            db.productDao().upsertProductWithParticipants(
                product = ProductEntity(
                    id = "prod-owner-only",
                    name = "Peça Especial",
                    priceCents = 4_000L,
                    active = true,
                    feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL.storageKey,
                    feeOwnerPersonId = ownerOnly.id,
                    createdAtEpochMs = 1_730_000_000_000L,
                    updatedAtEpochMs = 1_730_000_000_000L,
                ),
                participants = listOf(
                    ProductParticipantEntity(
                        productId = "prod-owner-only",
                        personId = participant.id,
                        amountCents = 4_000L,
                        sortOrder = 0,
                    ),
                ),
                isNew = true,
            )

            val ownerOnlyError = runCatching { repo.deletePerson(ownerOnly.id) }.exceptionOrNull()
            assertTrue(ownerOnlyError is CatalogValidationException)
            assertEquals(
                "Não é possível excluir \"Eduarda\" porque ela está vinculada a produto(s) existente(s).",
                ownerOnlyError?.message,
            )

            // Confirma preservação dos dois produtos e das pessoas
            assertNotNull(db.personDao().getById(owner.id))
            assertNotNull(db.personDao().getById(ownerOnly.id))
            assertEquals(2, db.productDao().getAllProducts().size)
            assertNotNull(db.productDao().getWithParticipantsById(prodIndividual.id))

            // Confirma que ON DELETE RESTRICT continua ativo no SQLite
            db.openHelper.writableDatabase.query("PRAGMA foreign_keys").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            val directFkError = runCatching { db.personDao().deleteById(ownerOnly.id) }.exceptionOrNull()
            assertTrue(directFkError is SQLiteConstraintException)
        } finally {
            db.close()
        }
    }

    @Test
    fun `foreign key constraint and database errors behave safely without partial deletion`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = openInMemoryDb(context)
        try {
            val realDao = db.personDao()

            // 1) Violação de FK simulada na exclusão vira CatalogValidationException e não oculta nome
            val fkPerson = RoomCatalogRepository(realDao, db.productDao()).createPerson("Fernanda")
            val fkThrowingDao = object : PersonDao by realDao {
                override suspend fun deleteById(id: String): Int {
                    throw SQLiteConstraintException("FOREIGN KEY constraint failed (code 787 SQLITE_CONSTRAINT_FOREIGNKEY)")
                }

                override suspend fun deletePersonSafely(personId: String): PersonDeletionResult {
                    val existing = getById(personId) ?: return PersonDeletionResult.NotFound
                    return try {
                        deleteById(personId)
                        PersonDeletionResult.Deleted
                    } catch (e: SQLiteConstraintException) {
                        throw e
                    }
                }
            }
            val fkRepo = RoomCatalogRepository(fkThrowingDao, db.productDao())
            val fkResult = runCatching { fkRepo.deletePerson(fkPerson.id) }.exceptionOrNull()
            assertTrue(fkResult is CatalogValidationException)
            assertEquals(
                "Não é possível excluir \"Fernanda\" porque ela está vinculada a produto(s) existente(s).",
                fkResult?.message,
            )
            assertNotNull(realDao.getById(fkPerson.id))

            // 2) Erro não relacionado de banco NÃO é escondido e não causa exclusão parcial
            val dbErrorDao = object : PersonDao by realDao {
                override suspend fun deletePersonSafely(personId: String): PersonDeletionResult {
                    throw SQLiteException("simulated disk I/O error")
                }
            }
            val dbErrorRepo = RoomCatalogRepository(dbErrorDao, db.productDao())
            val dbError = runCatching { dbErrorRepo.deletePerson(fkPerson.id) }.exceptionOrNull()
            assertTrue(dbError is SQLiteException)
            assertEquals("simulated disk I/O error", dbError?.message)
            assertNotNull(realDao.getById(fkPerson.id))

            // 3) Quando deleteById retorna 0 linhas afetadas, lança a mensagem esperada
            val zeroRowsDao = object : PersonDao by realDao {
                override suspend fun deletePersonSafely(personId: String): PersonDeletionResult {
                    return PersonDeletionResult.DeleteFailed
                }
            }
            val zeroRowsRepo = RoomCatalogRepository(zeroRowsDao, db.productDao())
            val zeroRowsError = runCatching { zeroRowsRepo.deletePerson(fkPerson.id) }.exceptionOrNull()
            assertTrue(zeroRowsError is CatalogValidationException)
            assertEquals("Não foi possível excluir a pessoa selecionada.", zeroRowsError?.message)
            assertNotNull(realDao.getById(fkPerson.id))
        } finally {
            db.close()
        }
    }

    @Test
    fun `verification and deletion execute inside the same active Room transaction`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recordedStatements = Collections.synchronizedList(mutableListOf<Pair<String, Boolean>>())
        lateinit var db: AppDatabase
        db = openInMemoryDb(
            context = context,
            queryCallback = RoomDatabase.QueryCallback { sqlQuery, _ ->
                recordedStatements.add(sqlQuery to db.inTransaction())
            },
        )
        try {
            val repo = RoomCatalogRepository(db.personDao(), db.productDao())
            val person = repo.createPerson("Gabriel")

            recordedStatements.clear()
            repo.deletePerson(person.id)

            assertFalse(db.inTransaction())
            assertNull(db.personDao().getById(person.id))

            val personSelect = recordedStatements.firstOrNull { it.first.contains("FROM people WHERE id =", ignoreCase = true) }
            val participantCount = recordedStatements.firstOrNull { it.first.contains("FROM product_participants WHERE person_id =", ignoreCase = true) }
            val ownerCount = recordedStatements.firstOrNull { it.first.contains("FROM products WHERE fee_owner_person_id =", ignoreCase = true) }
            val personDelete = recordedStatements.firstOrNull { it.first.contains("DELETE FROM people WHERE id =", ignoreCase = true) }

            assertNotNull(personSelect)
            assertNotNull(participantCount)
            assertNotNull(ownerCount)
            assertNotNull(personDelete)

            assertTrue("SELECT de pessoa deve ocorrer dentro de transação", personSelect!!.second)
            assertTrue("Contagem de product_participants deve ocorrer dentro de transação", participantCount!!.second)
            assertTrue("Contagem de fee_owner_person_id deve ocorrer dentro de transação", ownerCount!!.second)
            assertTrue("DELETE de pessoa deve ocorrer dentro de transação", personDelete!!.second)
        } finally {
            db.close()
        }
    }

    @Test
    fun `transaction rollback preserves person when failure occurs inside deletePersonSafely`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = openInMemoryDb(context)
        try {
            val repo = RoomCatalogRepository(db.personDao(), db.productDao())
            val person = repo.createPerson("Helena")

            db.openHelper.writableDatabase.execSQL(
                """
                CREATE TEMP TRIGGER fail_delete_person
                BEFORE DELETE ON people
                BEGIN
                    SELECT RAISE(ABORT, 'injected failure during delete');
                END
                """.trimIndent(),
            )

            val error = runCatching { repo.deletePerson(person.id) }.exceptionOrNull()

            assertTrue(error is SQLiteException)
            assertTrue(error?.message?.contains("injected failure during delete") == true)
            assertNotNull(db.personDao().getById(person.id))
            assertFalse(db.inTransaction())

            db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS temp.fail_delete_person")
            repo.deletePerson(person.id)
            assertNull(db.personDao().getById(person.id))
        } finally {
            db.close()
        }
    }

    @Test
    fun `concurrent deletion vs product linking and double deletion remain atomic and consistent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "race.db"
        context.deleteDatabase(dbName)
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        val executor = Executors.newFixedThreadPool(2)
        try {
            val repo = RoomCatalogRepository(db.personDao(), db.productDao())

            // Parte 1: Exclusões concorrentes da mesma pessoa sem vínculos (sem Thread.sleep)
            repeat(10) { round ->
                val target = runBlocking { repo.createPerson("Concorrente $round") }
                val barrier = CyclicBarrier(2)
                val doneLatch = CountDownLatch(2)

                val f1 = executor.submit<Throwable?> {
                    barrier.await(5, TimeUnit.SECONDS)
                    try {
                        runBlocking { repo.deletePerson(target.id) }
                        null
                    } catch (t: Throwable) {
                        t
                    } finally {
                        doneLatch.countDown()
                    }
                }
                val f2 = executor.submit<Throwable?> {
                    barrier.await(5, TimeUnit.SECONDS)
                    try {
                        runBlocking { repo.deletePerson(target.id) }
                        null
                    } catch (t: Throwable) {
                        t
                    } finally {
                        doneLatch.countDown()
                    }
                }

                assertTrue(doneLatch.await(10, TimeUnit.SECONDS))
                val r1 = f1.get()
                val r2 = f2.get()

                val successes = listOf(r1, r2).count { it == null }
                val failures = listOfNotNull(r1, r2)
                assertEquals(1, successes)
                assertEquals(1, failures.size)
                assertTrue(failures.first() is CatalogValidationException)
                assertEquals("Pessoa não encontrada para exclusão.", failures.first().message)
                assertNull(runBlocking { db.personDao().getById(target.id) })
            }

            // Parte 2: Exclusão concorrente vs vínculo direto de produto no banco (sem Thread.sleep)
            repeat(10) { round ->
                val target = runBlocking { repo.createPerson("Vinculada $round") }
                val barrier = CyclicBarrier(2)
                val doneLatch = CountDownLatch(2)

                val deleteFuture = executor.submit<Throwable?> {
                    barrier.await(5, TimeUnit.SECONDS)
                    try {
                        runBlocking { repo.deletePerson(target.id) }
                        null
                    } catch (t: Throwable) {
                        t
                    } finally {
                        doneLatch.countDown()
                    }
                }

                val linkFuture = executor.submit<Throwable?> {
                    barrier.await(5, TimeUnit.SECONDS)
                    try {
                        runBlocking {
                            db.productDao().upsertProductWithParticipants(
                                product = ProductEntity(
                                    id = "concurrent-prod-$round",
                                    name = "Produto $round",
                                    priceCents = 2_000L,
                                    active = true,
                                    feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL.storageKey,
                                    feeOwnerPersonId = target.id,
                                    createdAtEpochMs = 1_730_000_000_000L + round,
                                    updatedAtEpochMs = 1_730_000_000_000L + round,
                                ),
                                participants = listOf(
                                    ProductParticipantEntity(
                                        productId = "concurrent-prod-$round",
                                        personId = target.id,
                                        amountCents = 2_000L,
                                        sortOrder = 0,
                                    ),
                                ),
                                isNew = true,
                            )
                        }
                        null
                    } catch (t: Throwable) {
                        t
                    } finally {
                        doneLatch.countDown()
                    }
                }

                assertTrue(doneLatch.await(10, TimeUnit.SECONDS))
                val deleteError = deleteFuture.get()
                val linkError = linkFuture.get()

                val personAfter = runBlocking { db.personDao().getById(target.id) }
                val productAfter = runBlocking { db.productDao().getWithParticipantsById("concurrent-prod-$round") }

                if (deleteError == null) {
                    // Exclusão venceu: produto não pôde ser gravado sem a pessoa (sem órfãos)
                    assertTrue(linkError is SQLiteConstraintException)
                    assertNull(personAfter)
                    assertNull(productAfter)
                } else {
                    // Vínculo venceu: exclusão foi bloqueada via CatalogValidationException (nunca FK não tratada)
                    assertNull(linkError)
                    assertTrue(deleteError is CatalogValidationException)
                    assertEquals(
                        "Não é possível excluir \"${target.name}\" porque ela está vinculada a produto(s) existente(s).",
                        deleteError.message,
                    )
                    assertNotNull(personAfter)
                    assertNotNull(productAfter)
                }
            }
        } finally {
            executor.shutdownNow()
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    private fun assertFalse(value: Boolean) {
        org.junit.Assert.assertFalse(value)
    }
}
