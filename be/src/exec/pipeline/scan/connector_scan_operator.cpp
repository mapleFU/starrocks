// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.

#include "exec/pipeline/scan/connector_scan_operator.h"

#include "column/chunk.h"
#include "exec/pipeline/scan/balanced_chunk_buffer.h"
#include "exec/vectorized/connector_scan_node.h"
#include "exec/workgroup/work_group.h"
#include "runtime/exec_env.h"
#include "runtime/runtime_state.h"

namespace starrocks::pipeline {

// ==================== ConnectorScanOperatorFactory ====================

ConnectorScanOperatorFactory::ConnectorScanOperatorFactory(int32_t id, ScanNode* scan_node, size_t dop,
                                                           ChunkBufferLimiterPtr buffer_limiter)
        : ScanOperatorFactory(id, scan_node), _chunk_buffer(BalanceStrategy::kDirect, dop, std::move(buffer_limiter)) {}

Status ConnectorScanOperatorFactory::do_prepare(RuntimeState* state) {
    const auto& conjunct_ctxs = _scan_node->conjunct_ctxs();
    RETURN_IF_ERROR(Expr::prepare(conjunct_ctxs, state));
    RETURN_IF_ERROR(Expr::open(conjunct_ctxs, state));

    return Status::OK();
}

void ConnectorScanOperatorFactory::do_close(RuntimeState* state) {
    const auto& conjunct_ctxs = _scan_node->conjunct_ctxs();
    Expr::close(conjunct_ctxs, state);
}

OperatorPtr ConnectorScanOperatorFactory::do_create(int32_t dop, int32_t driver_sequence) {
    return std::make_shared<ConnectorScanOperator>(this, _id, driver_sequence, dop, _scan_node);
}

// ==================== ConnectorScanOperator ====================

ConnectorScanOperator::ConnectorScanOperator(OperatorFactory* factory, int32_t id, int32_t driver_sequence, int32_t dop,
                                             ScanNode* scan_node)
        : ScanOperator(factory, id, driver_sequence, dop, scan_node) {}

Status ConnectorScanOperator::do_prepare(RuntimeState* state) {
    return Status::OK();
}

void ConnectorScanOperator::do_close(RuntimeState* state) {}

ChunkSourcePtr ConnectorScanOperator::create_chunk_source(MorselPtr morsel, int32_t chunk_source_index) {
    vectorized::ConnectorScanNode* scan_node = down_cast<vectorized::ConnectorScanNode*>(_scan_node);
    ConnectorScanOperatorFactory* factory = down_cast<ConnectorScanOperatorFactory*>(_factory);
    return std::make_shared<ConnectorChunkSource>(_driver_sequence, _chunk_source_profiles[chunk_source_index].get(),
                                                  std::move(morsel), this, scan_node, factory->get_chunk_buffer());
}

void ConnectorScanOperator::attach_chunk_source(int32_t source_index) {
    auto* factory = down_cast<ConnectorScanOperatorFactory*>(_factory);
    auto& active_inputs = factory->get_active_inputs();
    auto key = std::make_pair(_driver_sequence, source_index);
    active_inputs.emplace(key);
}

void ConnectorScanOperator::detach_chunk_source(int32_t source_index) {
    auto* factory = down_cast<ConnectorScanOperatorFactory*>(_factory);
    auto& active_inputs = factory->get_active_inputs();
    auto key = std::make_pair(_driver_sequence, source_index);
    active_inputs.erase(key);
}

bool ConnectorScanOperator::has_shared_chunk_source() const {
    auto* factory = down_cast<ConnectorScanOperatorFactory*>(_factory);
    auto& active_inputs = factory->get_active_inputs();
    return !active_inputs.empty();
}

size_t ConnectorScanOperator::num_buffered_chunks() const {
    auto* factory = down_cast<ConnectorScanOperatorFactory*>(_factory);
    auto& buffer = factory->get_chunk_buffer();
    return buffer.size(_driver_sequence);
}

ChunkPtr ConnectorScanOperator::get_chunk_from_buffer() {
    auto* factory = down_cast<ConnectorScanOperatorFactory*>(_factory);
    auto& buffer = factory->get_chunk_buffer();
    vectorized::ChunkPtr chunk = nullptr;
    if (buffer.try_get(_driver_sequence, &chunk)) {
        return chunk;
    }
    return nullptr;
}

size_t ConnectorScanOperator::buffer_size() const {
    auto* factory = down_cast<ConnectorScanOperatorFactory*>(_factory);
    auto& buffer = factory->get_chunk_buffer();
    return buffer.limiter()->size();
}

size_t ConnectorScanOperator::buffer_capacity() const {
    auto* factory = down_cast<ConnectorScanOperatorFactory*>(_factory);
    auto& buffer = factory->get_chunk_buffer();
    return buffer.limiter()->capacity();
}

size_t ConnectorScanOperator::default_buffer_capacity() const {
    auto* factory = down_cast<ConnectorScanOperatorFactory*>(_factory);
    auto& buffer = factory->get_chunk_buffer();
    return buffer.limiter()->default_capacity();
}

ChunkBufferTokenPtr ConnectorScanOperator::pin_chunk(int num_chunks) {
    auto* factory = down_cast<ConnectorScanOperatorFactory*>(_factory);
    auto& buffer = factory->get_chunk_buffer();
    return buffer.limiter()->pin(num_chunks);
}

bool ConnectorScanOperator::is_buffer_full() const {
    auto* factory = down_cast<ConnectorScanOperatorFactory*>(_factory);
    auto& buffer = factory->get_chunk_buffer();
    return buffer.limiter()->is_full();
}

void ConnectorScanOperator::set_buffer_finished() {
    auto* factory = down_cast<ConnectorScanOperatorFactory*>(_factory);
    auto& buffer = factory->get_chunk_buffer();
    buffer.set_finished(_driver_sequence);
}

connector::ConnectorType ConnectorScanOperator::connector_type() {
    auto* scan_node = down_cast<vectorized::ConnectorScanNode*>(_scan_node);
    return scan_node->connector_type();
}

// ==================== ConnectorChunkSource ====================
ConnectorChunkSource::ConnectorChunkSource(int32_t scan_operator_id, RuntimeProfile* runtime_profile,
                                           MorselPtr&& morsel, ScanOperator* op,
                                           vectorized::ConnectorScanNode* scan_node, BalancedChunkBuffer& chunk_buffer)
        : ChunkSource(scan_operator_id, runtime_profile, std::move(morsel), chunk_buffer),
          _scan_node(scan_node),
          _limit(scan_node->limit()),
          _runtime_in_filters(op->runtime_in_filters()),
          _runtime_bloom_filters(op->runtime_bloom_filters()) {
    _conjunct_ctxs = scan_node->conjunct_ctxs();
    _conjunct_ctxs.insert(_conjunct_ctxs.end(), _runtime_in_filters.begin(), _runtime_in_filters.end());
    ScanMorsel* scan_morsel = (ScanMorsel*)_morsel.get();
    TScanRange* scan_range = scan_morsel->get_scan_range();

    if (scan_range->__isset.broker_scan_range) {
        scan_range->broker_scan_range.params.__set_non_blocking_read(true);
    }
    _data_source = scan_node->data_source_provider()->create_data_source(*scan_range);
    _data_source->set_predicates(_conjunct_ctxs);
    _data_source->set_runtime_filters(_runtime_bloom_filters);
    _data_source->set_read_limit(_limit);
    _data_source->set_runtime_profile(runtime_profile);
}

ConnectorChunkSource::~ConnectorChunkSource() {
    if (_runtime_state != nullptr) {
        close(_runtime_state);
    }
}

Status ConnectorChunkSource::prepare(RuntimeState* state) {
    RETURN_IF_ERROR(ChunkSource::prepare(state));
    _runtime_state = state;
    return Status::OK();
}

void ConnectorChunkSource::close(RuntimeState* state) {
    if (_closed) return;
    _closed = true;
    _data_source->close(state);
}

Status ConnectorChunkSource::_read_chunk(RuntimeState* state, vectorized::ChunkPtr* chunk) {
    if (!_opened) {
        RETURN_IF_ERROR(_data_source->open(state));
        _opened = true;
    }

    if (state->is_cancelled()) {
        return Status::Cancelled("canceled state");
    }

    // Improve for select * from table limit x, x is small
    if (_limit != -1 && _rows_read >= _limit) {
        return Status::EndOfFile("limit reach");
    }

    do {
        RETURN_IF_ERROR(_data_source->get_next(state, chunk));
    } while ((*chunk)->num_rows() == 0);

    _rows_read += (*chunk)->num_rows();
    _scan_rows_num = _data_source->raw_rows_read();
    _scan_bytes = _data_source->num_bytes_read();

    return Status::OK();
}

const workgroup::WorkGroupScanSchedEntity* ConnectorChunkSource::_scan_sched_entity(
        const workgroup::WorkGroup* wg) const {
    DCHECK(wg != nullptr);
    return wg->connector_scan_sched_entity();
}

} // namespace starrocks::pipeline
