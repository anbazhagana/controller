/*
 * Copyright (c) 2015 Cisco Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.dsbenchmark.txchain;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.BindingTransactionChain;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChain;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.dsbenchmark.BaListBuilder;
import org.opendaylight.dsbenchmark.DatastoreAbstractWriter;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dsbenchmark.rev150105.StartTestInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dsbenchmark.rev150105.StartTestInput.DataStore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dsbenchmark.rev150105.StartTestInput.Operation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dsbenchmark.rev150105.TestExec;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dsbenchmark.rev150105.test.exec.OuterList;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TxchainBaWrite extends DatastoreAbstractWriter implements TransactionChainListener {
    private static final Logger LOG = LoggerFactory.getLogger(TxchainBaWrite.class);
    private final DataBroker bindingDataBroker;
    private List<OuterList> list;

    public TxchainBaWrite(final DataBroker bindingDataBroker, final Operation oper,
                          final int outerListElem, final int innerListElem, final long writesPerTx, final DataStore dataStore) {
        super(oper, outerListElem, innerListElem, writesPerTx, dataStore);
        this.bindingDataBroker = bindingDataBroker;
        LOG.debug("Created TxchainBaWrite");
    }

    @Override
    public void createList() {
        list = BaListBuilder.buildOuterList(this.outerListElem, this.innerListElem);
    }

    @Override
    public void executeList() {
        final BindingTransactionChain chain = bindingDataBroker.createTransactionChain(this);
        final LogicalDatastoreType dsType = getDataStoreType();

        WriteTransaction tx = chain.newWriteOnlyTransaction();
        int txSubmitted = 0;
        int writeCnt = 0;

        for (OuterList element : this.list) {
            InstanceIdentifier<OuterList> iid = InstanceIdentifier.create(TestExec.class)
                                                    .child(OuterList.class, element.key());

            if (oper == StartTestInput.Operation.PUT) {
                tx.put(dsType, iid, element);
            } else {
                tx.merge(dsType, iid, element);
            }

            writeCnt++;

            if (writeCnt == writesPerTx) {
                txSubmitted++;
                Futures.addCallback(tx.submit(), new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(final Void result) {
                        txOk++;
                    }

                    @Override
                    public void onFailure(final Throwable t) {
                        LOG.error("Transaction failed, {}", t);
                        txError++;
                    }
                }, MoreExecutors.directExecutor());
                tx = chain.newWriteOnlyTransaction();
                writeCnt = 0;
            }
        }

        // *** Clean up and close the transaction chain ***
        // Submit the outstanding transaction even if it's empty and wait for it to finish
        // We need to empty the transaction chain before closing it
        try {
            txSubmitted++;
            tx.submit().checkedGet();
            txOk++;
        } catch (final TransactionCommitFailedException e) {
            LOG.error("Transaction failed", e);
            txError++;
        }
        try {
            chain.close();
        } catch (final IllegalStateException e) {
            LOG.error("Transaction close failed,", e);
        }
        LOG.debug("Transactions: submitted {}, completed {}", txSubmitted, txOk + txError);
    }

    @Override
    public void onTransactionChainFailed(final TransactionChain<?, ?> chain,
            final AsyncTransaction<?, ?> transaction, final Throwable cause) {
        LOG.error("Broken chain {} in DatastoreBaAbstractWrite, transaction {}, cause {}",
                chain, transaction.getIdentifier(), cause);
    }

    @Override
    public void onTransactionChainSuccessful(final TransactionChain<?, ?> chain) {
        LOG.debug("DatastoreBaAbstractWrite closed successfully, chain {}", chain);
    }

}