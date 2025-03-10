// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.

#include "storage/lake/async_delta_writer.h"

#include <gtest/gtest.h>

#include <random>

#include "column/chunk.h"
#include "column/datum_tuple.h"
#include "column/fixed_length_column.h"
#include "column/schema.h"
#include "column/vectorized_fwd.h"
#include "common/logging.h"
#include "fs/fs_util.h"
#include "runtime/mem_tracker.h"
#include "storage/chunk_helper.h"
#include "storage/lake/fixed_location_provider.h"
#include "storage/lake/join_path.h"
#include "storage/lake/tablet.h"
#include "storage/lake/tablet_manager.h"
#include "storage/lake/txn_log.h"
#include "storage/rowset/segment.h"
#include "storage/rowset/segment_iterator.h"
#include "storage/rowset/segment_options.h"
#include "storage/tablet_schema.h"
#include "testutil/assert.h"
#include "testutil/id_generator.h"
#include "util/countdown_latch.h"
#include "util/defer_op.h"

namespace starrocks::lake {

using namespace starrocks::vectorized;

using VSchema = starrocks::vectorized::Schema;
using VChunk = starrocks::vectorized::Chunk;

class AsyncDeltaWriterTest : public testing::Test {
public:
    AsyncDeltaWriterTest() {
        _tablet_manager = ExecEnv::GetInstance()->lake_tablet_manager();

        _parent_mem_tracker = std::make_unique<MemTracker>(-1);
        _mem_tracker = std::make_unique<MemTracker>(-1, "", _parent_mem_tracker.get());
        _location_provider = std::make_unique<FixedLocationProvider>(kTestGroupPath);
        _backup_location_provider = _tablet_manager->TEST_set_location_provider(_location_provider.get());

        _tablet_metadata = std::make_unique<TabletMetadata>();
        _tablet_metadata->set_id(next_id());
        _tablet_metadata->set_version(1);
        //
        //  | column | type | KEY | NULL |
        //  +--------+------+-----+------+
        //  |   c0   |  INT | YES |  NO  |
        //  |   c1   |  INT | NO  |  NO  |
        auto schema = _tablet_metadata->mutable_schema();
        schema->set_id(next_id());
        schema->set_num_short_key_columns(1);
        schema->set_keys_type(DUP_KEYS);
        schema->set_num_rows_per_row_block(65535);
        schema->set_compress_kind(COMPRESS_LZ4);
        auto c0 = schema->add_column();
        {
            c0->set_unique_id(next_id());
            c0->set_name("c0");
            c0->set_type("INT");
            c0->set_is_key(true);
            c0->set_is_nullable(false);
        }
        auto c1 = schema->add_column();
        {
            c1->set_unique_id(next_id());
            c1->set_name("c1");
            c1->set_type("INT");
            c1->set_is_key(false);
            c1->set_is_nullable(false);
        }

        _tablet_schema = TabletSchema::create(*schema);
        _schema = std::make_shared<VSchema>(ChunkHelper::convert_schema(*_tablet_schema));
    }

protected:
    void SetUp() override {
        (void)ExecEnv::GetInstance()->lake_tablet_manager()->TEST_set_location_provider(_location_provider.get());
        (void)fs::remove_all(kTestGroupPath);
        CHECK_OK(fs::create_directories(lake::join_path(kTestGroupPath, lake::kSegmentDirectoryName)));
        CHECK_OK(fs::create_directories(lake::join_path(kTestGroupPath, lake::kMetadataDirectoryName)));
        CHECK_OK(fs::create_directories(lake::join_path(kTestGroupPath, lake::kTxnLogDirectoryName)));
        CHECK_OK(_tablet_manager->put_tablet_metadata(*_tablet_metadata));
    }

    void TearDown() override {
        ASSIGN_OR_ABORT(auto tablet, _tablet_manager->get_tablet(_tablet_metadata->id()));
        tablet.delete_txn_log(_txn_id);
        _txn_id++;
        (void)ExecEnv::GetInstance()->lake_tablet_manager()->TEST_set_location_provider(_backup_location_provider);
        (void)fs::remove_all(kTestGroupPath);
    }

    VChunk generate_data(int64_t chunk_size) {
        std::vector<int> v0(chunk_size);
        std::vector<int> v1(chunk_size);
        for (int i = 0; i < chunk_size; i++) {
            v0[i] = i;
        }
        auto rng = std::default_random_engine{};
        std::shuffle(v0.begin(), v0.end(), rng);
        for (int i = 0; i < chunk_size; i++) {
            v1[i] = v0[i] * 3;
        }

        auto c0 = Int32Column::create();
        auto c1 = Int32Column::create();
        c0->append_numbers(v0.data(), v0.size() * sizeof(int));
        c1->append_numbers(v1.data(), v1.size() * sizeof(int));
        return VChunk({c0, c1}, _schema);
    }

    constexpr static const char* const kTestGroupPath = "test_lake_async_delta_writer";

    TabletManager* _tablet_manager;
    std::unique_ptr<MemTracker> _parent_mem_tracker;
    std::unique_ptr<MemTracker> _mem_tracker;
    std::unique_ptr<FixedLocationProvider> _location_provider;
    LocationProvider* _backup_location_provider;
    std::unique_ptr<TabletMetadata> _tablet_metadata;
    std::shared_ptr<TabletSchema> _tablet_schema;
    std::shared_ptr<VSchema> _schema;
    int64_t _txn_id = 123;
    int64_t _partition_id = 456;
};

TEST_F(AsyncDeltaWriterTest, test_open) {
    // Invalid tablet id
    {
        auto tablet_id = -1;
        auto delta_writer = AsyncDeltaWriter::create(tablet_id, _txn_id, _partition_id, nullptr, _mem_tracker.get());
        ASSERT_ERROR(delta_writer->open());
        ASSERT_ERROR(delta_writer->open());
        delta_writer->close();
    }
    // Call open() multiple times
    {
        auto tablet_id = _tablet_metadata->id();
        auto delta_writer = AsyncDeltaWriter::create(tablet_id, _txn_id, _partition_id, nullptr, _mem_tracker.get());
        ASSERT_OK(delta_writer->open());
        ASSERT_OK(delta_writer->open());
        ASSERT_OK(delta_writer->open());
        delta_writer->close();
    }
    // Call open() concurrently
    {
        auto tablet_id = _tablet_metadata->id();
        auto delta_writer = AsyncDeltaWriter::create(tablet_id, _txn_id, _partition_id, nullptr, _mem_tracker.get());
        auto t1 = std::thread([&]() {
            for (int i = 0; i < 10000; i++) {
                ASSERT_OK(delta_writer->open());
            }
        });
        auto t2 = std::thread([&]() {
            for (int i = 0; i < 10000; i++) {
                ASSERT_OK(delta_writer->open());
            }
        });
        t1.join();
        t2.join();
        delta_writer->close();
    }
}

TEST_F(AsyncDeltaWriterTest, test_write) {
    // Prepare data for writing
    static const int kChunkSize = 128;
    auto chunk0 = generate_data(kChunkSize);
    auto indexes = std::vector<uint32_t>(kChunkSize);
    for (int i = 0; i < kChunkSize; i++) {
        indexes[i] = i;
    }

    // Create and open DeltaWriter
    auto tablet_id = _tablet_metadata->id();
    auto delta_writer = AsyncDeltaWriter::create(tablet_id, _txn_id, _partition_id, nullptr, _mem_tracker.get());
    ASSERT_OK(delta_writer->open());
    // Call open() again
    ASSERT_OK(delta_writer->open());

    CountDownLatch latch(1);
    // Write
    delta_writer->write(&chunk0, indexes.data(), indexes.size(), [&](const Status& st) { ASSERT_OK(st); });
    // Write
    delta_writer->write(&chunk0, indexes.data(), indexes.size(), [&](const Status& st) { ASSERT_OK(st); });
    // finish
    delta_writer->finish([&](const Status& st) {
        ASSERT_OK(st);
        latch.count_down();
    });

    latch.wait();

    // close
    delta_writer->close();

    // Check TxnLog
    ASSIGN_OR_ABORT(auto tablet, _tablet_manager->get_tablet(tablet_id));
    ASSIGN_OR_ABORT(auto txnlog, tablet.get_txn_log(_txn_id));
    ASSERT_EQ(tablet_id, txnlog->tablet_id());
    ASSERT_EQ(_txn_id, txnlog->txn_id());
    ASSERT_TRUE(txnlog->has_op_write());
    ASSERT_FALSE(txnlog->has_op_compaction());
    ASSERT_FALSE(txnlog->has_op_schema_change());
    ASSERT_TRUE(txnlog->op_write().has_rowset());
    ASSERT_EQ(1, txnlog->op_write().rowset().segments_size());
    ASSERT_FALSE(txnlog->op_write().rowset().overlapped());
    ASSERT_EQ(2 * kChunkSize, txnlog->op_write().rowset().num_rows());
    ASSERT_GT(txnlog->op_write().rowset().data_size(), 0);
    ASSERT_EQ(0, txnlog->op_write().rowset().del_vectors_size());

    // Check segment file
    ASSIGN_OR_ABORT(auto fs, FileSystem::CreateSharedFromString(kTestGroupPath));
    auto path0 = _location_provider->segment_location(tablet_id, txnlog->op_write().rowset().segments(0));

    ASSIGN_OR_ABORT(auto seg0, Segment::open(fs, path0, 0, _tablet_schema.get()));

    OlapReaderStatistics statistics;
    SegmentReadOptions opts;
    opts.fs = fs;
    opts.tablet_id = tablet_id;
    opts.stats = &statistics;
    opts.chunk_size = 1024;

    auto check_segment = [&](SegmentSharedPtr segment) {
        ASSIGN_OR_ABORT(auto seg_iter, segment->new_iterator(*_schema, opts));
        auto read_chunk_ptr = ChunkHelper::new_chunk(*_schema, 1024);
        ASSERT_OK(seg_iter->get_next(read_chunk_ptr.get()));
        ASSERT_EQ(2 * kChunkSize, read_chunk_ptr->num_rows());
        for (int i = 0; i < read_chunk_ptr->num_rows(); i++) {
            EXPECT_EQ(i / 2, read_chunk_ptr->get(i)[0].get_int32());
            EXPECT_EQ(i / 2 * 3, read_chunk_ptr->get(i)[1].get_int32());
        }
        read_chunk_ptr->reset();
        ASSERT_TRUE(seg_iter->get_next(read_chunk_ptr.get()).is_end_of_file());
        seg_iter->close();
    };

    check_segment(seg0);
}

TEST_F(AsyncDeltaWriterTest, test_write_concurrently) {
    // Prepare data for writing
    static const int kChunkSize = 128;
    auto chunk0 = generate_data(kChunkSize);
    auto indexes = std::vector<uint32_t>(kChunkSize);
    for (int i = 0; i < kChunkSize; i++) {
        indexes[i] = i;
    }

    // Create and open DeltaWriter
    auto tablet_id = _tablet_metadata->id();
    auto delta_writer = AsyncDeltaWriter::create(tablet_id, _txn_id, _partition_id, nullptr, _mem_tracker.get());

    ASSERT_OK(delta_writer->open());

    constexpr int kNumThreads = 3;
    constexpr int kChunksPerThread = 10;
    constexpr int kDuplicatedRows = kNumThreads * kChunksPerThread;

    std::atomic<int> started{false};
    auto f = [&]() {
        while (!started.load()) {
            std::this_thread::yield();
        }
        for (int i = 0; i < kChunksPerThread; i++) {
            delta_writer->write(&chunk0, indexes.data(), indexes.size(), [&](const Status& st) { ASSERT_OK(st); });
        }
    };

    std::vector<std::thread> threads;
    for (int i = 0; i < kNumThreads; i++) {
        threads.emplace_back(f);
    }
    started.store(true);
    for (auto& t : threads) {
        t.join();
    }

    // finish
    CountDownLatch latch(1);
    delta_writer->finish([&](const Status& st) {
        ASSERT_OK(st);
        latch.count_down();
    });

    latch.wait();

    // close
    delta_writer->close();

    // Check TxnLog
    ASSIGN_OR_ABORT(auto tablet, _tablet_manager->get_tablet(tablet_id));
    ASSIGN_OR_ABORT(auto txnlog, tablet.get_txn_log(_txn_id));
    ASSERT_EQ(tablet_id, txnlog->tablet_id());
    ASSERT_EQ(_txn_id, txnlog->txn_id());
    ASSERT_TRUE(txnlog->has_op_write());
    ASSERT_FALSE(txnlog->has_op_compaction());
    ASSERT_FALSE(txnlog->has_op_schema_change());
    ASSERT_TRUE(txnlog->op_write().has_rowset());
    ASSERT_EQ(1, txnlog->op_write().rowset().segments_size());
    ASSERT_FALSE(txnlog->op_write().rowset().overlapped());
    ASSERT_EQ(kNumThreads * kChunksPerThread * kChunkSize, txnlog->op_write().rowset().num_rows());
    ASSERT_GT(txnlog->op_write().rowset().data_size(), 0);
    ASSERT_EQ(0, txnlog->op_write().rowset().del_vectors_size());

    // Check segment file
    ASSIGN_OR_ABORT(auto fs, FileSystem::CreateSharedFromString(kTestGroupPath));
    auto path0 = _location_provider->segment_location(tablet_id, txnlog->op_write().rowset().segments(0));

    ASSIGN_OR_ABORT(auto seg0, Segment::open(fs, path0, 0, _tablet_schema.get()));

    OlapReaderStatistics statistics;
    SegmentReadOptions opts;
    opts.fs = fs;
    opts.tablet_id = tablet_id;
    opts.stats = &statistics;
    opts.chunk_size = kNumThreads * kChunksPerThread * kChunkSize;

    auto check_segment = [&](SegmentSharedPtr segment) {
        ASSIGN_OR_ABORT(auto seg_iter, segment->new_iterator(*_schema, opts));
        auto read_chunk_ptr = ChunkHelper::new_chunk(*_schema, opts.chunk_size);
        ASSERT_OK(seg_iter->get_next(read_chunk_ptr.get()));
        ASSERT_EQ(opts.chunk_size, read_chunk_ptr->num_rows());
        for (int i = 0; i < read_chunk_ptr->num_rows(); i++) {
            EXPECT_EQ(i / kDuplicatedRows, read_chunk_ptr->get(i)[0].get_int32());
            EXPECT_EQ((i / kDuplicatedRows) * 3, read_chunk_ptr->get(i)[1].get_int32());
        }
        read_chunk_ptr->reset();
        ASSERT_TRUE(seg_iter->get_next(read_chunk_ptr.get()).is_end_of_file());
        seg_iter->close();
    };

    check_segment(seg0);
}

TEST_F(AsyncDeltaWriterTest, test_write_after_close) {
    // Prepare data for writing
    static const int kChunkSize = 128;
    auto chunk0 = generate_data(kChunkSize);
    auto indexes = std::vector<uint32_t>(kChunkSize);
    for (int i = 0; i < kChunkSize; i++) {
        indexes[i] = i;
    }

    // Create and open DeltaWriter
    auto tablet_id = _tablet_metadata->id();
    auto delta_writer = AsyncDeltaWriter::create(tablet_id, _txn_id, _partition_id, nullptr, _mem_tracker.get());
    ASSERT_OK(delta_writer->open());

    // close()
    delta_writer->close();

    auto tid = std::this_thread::get_id();
    // write()
    delta_writer->write(&chunk0, indexes.data(), indexes.size(), [&](const Status& st) {
        ASSERT_ERROR(st);
        ASSERT_EQ(tid, std::this_thread::get_id());
    });

    // TxnLog should not exist
    ASSIGN_OR_ABORT(auto tablet, _tablet_manager->get_tablet(tablet_id));
    ASSERT_TRUE(tablet.get_txn_log(_txn_id).status().is_not_found());

    // Segment file should not exist
    ASSIGN_OR_ABORT(auto fs, FileSystem::CreateSharedFromString(kTestGroupPath));
    ASSERT_OK(fs->iterate_dir(join_path(kTestGroupPath, kMetadataDirectoryName), [&](std::string_view name) {
        EXPECT_TRUE(is_tablet_metadata(name)) << name;
        return true;
    }));
}

TEST_F(AsyncDeltaWriterTest, test_finish_after_close) {
    // Create and open DeltaWriter
    auto tablet_id = _tablet_metadata->id();
    auto delta_writer = AsyncDeltaWriter::create(tablet_id, _txn_id, _partition_id, nullptr, _mem_tracker.get());
    ASSERT_OK(delta_writer->open());

    // close()
    delta_writer->close();

    auto tid = std::this_thread::get_id();
    // finish()
    delta_writer->finish([&](const Status& st) {
        ASSERT_ERROR(st);
        ASSERT_EQ(tid, std::this_thread::get_id());
    });
}

TEST_F(AsyncDeltaWriterTest, test_close) {
    // Call close() multiple times
    {
        auto tablet_id = _tablet_metadata->id();
        auto delta_writer = AsyncDeltaWriter::create(tablet_id, _txn_id, _partition_id, nullptr, _mem_tracker.get());
        ASSERT_OK(delta_writer->open());

        delta_writer->close();
        delta_writer->close();
    }
    // Call close() concurrently
    {
        auto tablet_id = _tablet_metadata->id();
        auto delta_writer = AsyncDeltaWriter::create(tablet_id, _txn_id, _partition_id, nullptr, _mem_tracker.get());
        ASSERT_OK(delta_writer->open());
        auto t1 = std::thread([&]() {
            for (int i = 0; i < 10000; i++) {
                delta_writer->close();
            }
        });
        auto t2 = std::thread([&]() {
            for (int i = 0; i < 10000; i++) {
                delta_writer->close();
            }
        });
        t1.join();
        t2.join();
    }
}

} // namespace starrocks::lake
