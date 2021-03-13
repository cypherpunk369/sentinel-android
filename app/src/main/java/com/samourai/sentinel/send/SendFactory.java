package com.samourai.sentinel.send;

import android.content.Context;
import android.widget.Toast;

import com.samourai.sentinel.R;
import com.samourai.sentinel.core.SentinelState;
import com.samourai.sentinel.core.hd.HD_WalletFactory;
import com.samourai.sentinel.core.segwit.SegwitAddress;
import com.samourai.sentinel.core.segwit.bech32.Bech32Util;
import com.samourai.sentinel.sweep.MyTransactionInput;
import com.samourai.sentinel.sweep.PrivKeyReader;
import com.samourai.sentinel.sweep.UTXO;
import com.samourai.sentinel.util.FormatsUtil;
import com.samourai.sentinel.util.PrefsUtil;
import com.samourai.wallet.send.MyTransactionOutPoint;
import com.samourai.wallet.util.FormatsUtilGeneric;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.DumpedPrivateKey;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.crypto.MnemonicException;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptException;
import org.bitcoinj.script.ScriptOpCodes;
import org.bouncycastle.util.encoders.Hex;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import timber.log.Timber;


public class SendFactory	{

    private static SendFactory instance = null;
    private static Context context = null;

    private SendFactory () { ; }

    public static SendFactory getInstance(Context ctx) {

        context = ctx;

        if(instance == null)	{
            instance = new SendFactory();
        }

        return instance;
    }

    public Transaction makeTransaction(final int accountIdx, final List<MyTransactionOutPoint> unspent, final HashMap<String, BigInteger> receivers) {

        Transaction tx = null;

        try {
//            int changeIdx = HD_WalletFactory.getInstance(context).get().getAccount(accountIdx).getChange().getAddrIdx();
            tx = makeTransaction(accountIdx, receivers, unspent);
        }
        catch(Exception e) {
            e.printStackTrace();
        }

        return tx;
    }

    public Transaction signTransaction(Transaction unsignedTx, int account)    {

        HashMap<String, ECKey> keyBag = new HashMap<String, ECKey>();

        for (TransactionInput input : unsignedTx.getInputs()) {

            try {
                byte[] scriptBytes = input.getOutpoint().getConnectedPubKeyScript();
                String address = null;
//                Log.i("SendFactory", "connected pubkey script:" + Hex.toHexString(scriptBytes));
                if(Bech32Util.getInstance().isBech32Script(Hex.toHexString(scriptBytes)))    {
                    address = Bech32Util.getInstance().getAddressFromScript(Hex.toHexString(scriptBytes));
                }
                else    {
                    address = new Script(scriptBytes).getToAddress(SentinelState.Companion.getNetworkParam()).toString();
                }
//                Log.i("SendFactory", "address from script:" + address);
                ECKey ecKey = null;
                ecKey = getPrivKey(address, account);
                if(ecKey != null) {
                    keyBag.put(input.getOutpoint().toString(), ecKey);
                }
                else {
                    throw new RuntimeException("ECKey error: cannot process private key");
//                    Log.i("ECKey error", "cannot process private key");
                }
            }
            catch(ScriptException se) {
                ;
            }
            catch(Exception e) {
                ;
            }

        }

        Transaction signedTx = signTransaction(unsignedTx, keyBag);
        if(signedTx == null)    {
            return null;
        }
        else    {
            String hexString = new String(Hex.encode(signedTx.bitcoinSerialize()));
            if(hexString.length() > (100 * 1024)) {
                Toast.makeText(context, R.string.tx_length_error, Toast.LENGTH_SHORT).show();
//              Log.i("SendFactory", "Transaction length too long");
            }

            return signedTx;
        }
    }

    public Transaction signTransactionForSweep(Transaction unsignedTx, PrivKeyReader privKeyReader)    {

        HashMap<String, ECKey> keyBag = new HashMap<String, ECKey>();

        for (TransactionInput input : unsignedTx.getInputs()) {

            try {
                byte[] scriptBytes = input.getOutpoint().getConnectedPubKeyScript();

                String script = Hex.toHexString(scriptBytes);
                String address = null;
                if(Bech32Util.getInstance().isBech32Script(script))    {
                    try {
                        address = Bech32Util.getInstance().getAddressFromScript(script);
                    }
                    catch(Exception e) {
                        ;
                    }
                }
                else    {
                    address = new Script(scriptBytes).getToAddress(SentinelState.Companion.getNetworkParam()).toString();
                }


                ECKey ecKey = null;
                try {
                    DumpedPrivateKey pk = new DumpedPrivateKey(SentinelState.Companion.getNetworkParam(), privKeyReader.getKey().getPrivateKeyAsWiF(SentinelState.Companion.getNetworkParam()));
                    ecKey = pk.getKey();
//                    Log.i("SendFactory", "ECKey address:" + ecKey.toAddress(SamouraiWallet.getInstance().getCurrentNetworkParams()).toString());
                } catch (AddressFormatException afe) {
                    afe.printStackTrace();
                    continue;
                }

                if(ecKey != null) {
                    keyBag.put(input.getOutpoint().toString(), ecKey);
                }
                else {
                    Toast.makeText(context, R.string.cannot_recognize_privkey, Toast.LENGTH_SHORT).show();
//                    Log.i("ECKey error", "cannot process private key");
                }
            }
            catch(ScriptException se) {
                ;
            }
            catch(Exception e) {
                ;
            }

        }

        Transaction signedTx = signTransaction(unsignedTx, keyBag);
        if(signedTx == null)    {
            return null;
        }
        else    {
            String hexString = new String(Hex.encode(signedTx.bitcoinSerialize()));
            if(hexString.length() > (100 * 1024)) {
                Toast.makeText(context, R.string.tx_length_error, Toast.LENGTH_SHORT).show();
//              Log.i("SendFactory", "Transaction length too long");
            }

            return signedTx;
        }
    }

    /*
    Used by spends
     */
    private Transaction makeTransaction(int accountIdx, HashMap<String, BigInteger> receivers, List<MyTransactionOutPoint> unspent) throws Exception {

        BigInteger amount = BigInteger.ZERO;
        for(Iterator<Map.Entry<String, BigInteger>> iterator = receivers.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<String, BigInteger> mapEntry = iterator.next();
            amount = amount.add(mapEntry.getValue());
        }

        List<TransactionOutput> outputs = new ArrayList<TransactionOutput>();
        Transaction tx = new Transaction(SentinelState.Companion.getNetworkParam());

        for(Iterator<Map.Entry<String, BigInteger>> iterator = receivers.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<String, BigInteger> mapEntry = iterator.next();
            String toAddress = mapEntry.getKey();
            BigInteger value = mapEntry.getValue();
/*
            if(value.compareTo(SamouraiWallet.bDust) < 1)    {
                throw new Exception(context.getString(R.string.dust_amount));
            }
*/
            if(value == null || (value.compareTo(BigInteger.ZERO) <= 0 && !FormatsUtilGeneric.getInstance().isValidBIP47OpReturn(toAddress))) {
                throw new Exception(context.getString(R.string.invalid_amount));
            }

            TransactionOutput output = null;
            Script toOutputScript = null;
            if(!FormatsUtil.Companion.isValidBitcoinAddress(toAddress) && FormatsUtilGeneric.getInstance().isValidBIP47OpReturn(toAddress))    {
                toOutputScript = new ScriptBuilder().op(ScriptOpCodes.OP_RETURN).data(Hex.decode(toAddress)).build();
                output = new TransactionOutput(SentinelState.Companion.getNetworkParam(), null, Coin.valueOf(0L), toOutputScript.getProgram());
            }
            else if(FormatsUtil.Companion.isValidBech32(toAddress))   {
                output = Bech32Util.getInstance().getTransactionOutput(toAddress, value.longValue());
            }
            else    {
                toOutputScript = ScriptBuilder.createOutputScript(org.bitcoinj.core.Address.fromBase58(SentinelState.Companion.getNetworkParam(), toAddress));
                output = new TransactionOutput(SentinelState.Companion.getNetworkParam(), null, Coin.valueOf(value.longValue()), toOutputScript.getProgram());
            }

            outputs.add(output);
        }

        List<MyTransactionInput> inputs = new ArrayList<MyTransactionInput>();
        for(MyTransactionOutPoint outPoint : unspent) {
            Script script = new Script(outPoint.getScriptBytes());

            if(script.getScriptType() == Script.ScriptType.NO_TYPE) {
                continue;
            }

            MyTransactionInput input = new MyTransactionInput(SentinelState.Companion.getNetworkParam(), null, new byte[0], outPoint, outPoint.getTxHash().toString(), outPoint.getTxOutputN());
//            if(PrefsUtil.getInstance(context).getValue(PrefsUtil.RBF_OPT_IN, false) == true)    {
//                input.setSequenceNumber(SamouraiWallet.RBF_SEQUENCE_VAL.longValue());
//            }
            inputs.add(input);
        }

        //
        // deterministically sort inputs and outputs, see BIP69 (OBPP)
        //
//        Collections.sort(inputs, new BIP69InputComparator());
        for(TransactionInput input : inputs) {
            tx.addInput(input);
        }

        Collections.sort(outputs, new com.samourai.sentinel.sweep.SendFactory.BIP69OutputComparator());
        for(TransactionOutput to : outputs) {
            tx.addOutput(to);
        }

        return tx;
    }

    private synchronized Transaction signTransaction(Transaction transaction, HashMap<String, ECKey> keyBag) throws ScriptException {

        List<TransactionInput> inputs = transaction.getInputs();

        TransactionInput input = null;
        TransactionOutput connectedOutput = null;
        byte[] connectedPubKeyScript = null;
        TransactionSignature sig = null;
        Script scriptPubKey = null;
        ECKey key = null;

        for (int i = 0; i < inputs.size(); i++) {

            input = inputs.get(i);

            key = keyBag.get(input.getOutpoint().toString());
            connectedPubKeyScript = input.getOutpoint().getConnectedPubKeyScript();
            connectedOutput = input.getOutpoint().getConnectedOutput();
            scriptPubKey = connectedOutput.getScriptPubKey();

            String script = Hex.toHexString(connectedPubKeyScript);
            String address = null;
            if(Bech32Util.getInstance().isBech32Script(script))    {
                try {
                    address = Bech32Util.getInstance().getAddressFromScript(script);
                }
                catch(Exception e) {
                    ;
                }
            }
            else    {
                address = new Script(connectedPubKeyScript).getToAddress(SentinelState.Companion.getNetworkParam()).toString();
            }

            if(FormatsUtil.Companion.isValidBech32(address) || Address.fromBase58(SentinelState.Companion.getNetworkParam(), address).isP2SHAddress())    {

                final SegwitAddress segwitAddress = new SegwitAddress(key.getPubKey(), SentinelState.Companion.getNetworkParam());
//                System.out.println("pubKey:" + Hex.toHexString(key.getPubKey()));
//                final Script scriptPubKey = p2shp2wpkh.segWitOutputScript();
//                System.out.println("scriptPubKey:" + Hex.toHexString(scriptPubKey.getProgram()));
//                System.out.println("to address from script:" + scriptPubKey.getToAddress(SamouraiWallet.getInstance().getCurrentNetworkParams()).toString());
                final Script redeemScript = segwitAddress.segWitRedeemScript();
//                System.out.println("redeem script:" + Hex.toHexString(redeemScript.getProgram()));
                final Script scriptCode = redeemScript.scriptCode();
//                System.out.println("script code:" + Hex.toHexString(scriptCode.getProgram()));

                sig = transaction.calculateWitnessSignature(i, key, scriptCode, connectedOutput.getValue(), Transaction.SigHash.ALL, false);
                final TransactionWitness witness = new TransactionWitness(2);
                witness.setPush(0, sig.encodeToBitcoin());
                witness.setPush(1, key.getPubKey());
                transaction.setWitness(i, witness);

                if(!FormatsUtil.Companion.isValidBech32(address) && Address.fromBase58(SentinelState.Companion.getNetworkParam(), address).isP2SHAddress())    {
                    final ScriptBuilder sigScript = new ScriptBuilder();
                    sigScript.data(redeemScript.getProgram());
                    transaction.getInput(i).setScriptSig(sigScript.build());
                    transaction.getInput(i).getScriptSig().correctlySpends(transaction, i, scriptPubKey, connectedOutput.getValue(), Script.ALL_VERIFY_FLAGS);
                }

            }
            else    {
                if(key != null && key.hasPrivKey() || key.isEncrypted()) {
                    sig = transaction.calculateSignature(i, key, connectedPubKeyScript, Transaction.SigHash.ALL, false);
                }
                else {
                    sig = TransactionSignature.dummy();   // watch only ?
                }

                if(scriptPubKey.isSentToAddress()) {
                    input.setScriptSig(ScriptBuilder.createInputScript(sig, key));
                }
                else if(scriptPubKey.isSentToRawPubKey()) {
                    input.setScriptSig(ScriptBuilder.createInputScript(sig));
                }
                else {
                    throw new RuntimeException("Unknown script type: " + scriptPubKey);
                }
            }

        }

        return transaction;

    }

    public Pair<ArrayList<MyTransactionOutPoint>, ArrayList<TransactionOutput>> boltzmann(List<UTXO> utxos, List<UTXO> utxosBis, BigInteger spendAmount, String address, int account) {

        Triple<ArrayList<MyTransactionOutPoint>, ArrayList<TransactionOutput>, ArrayList<UTXO>> set0 = boltzmannSet(utxos, spendAmount, address, null, account, null);
        if(set0 == null)    {
            return null;
        }
        Timber.d( "set0 utxo returned:" + set0.getRight().toString());

        long set0Value = 0L;
        for(UTXO u : set0.getRight())   {
            set0Value += u.getValue();
        }

        long utxosBisValue = 0L;
        if(utxosBis != null)    {
            for(UTXO u : utxosBis)   {
                utxosBisValue += u.getValue();
            }
        }

        Timber.d( "set0 value:" + set0Value);
        Timber.d( "utxosBis value:" + utxosBisValue);

        List<UTXO> _utxo = null;
        if(set0.getRight() != null && set0.getRight().size() > 0 && set0Value > spendAmount.longValue())    {
            Timber.d( "set0 selected for 2nd pass");
            _utxo = set0.getRight();
        }
        else if(utxosBis != null && utxosBisValue > spendAmount.longValue())   {
            Timber.d( "utxosBis selected for 2nd pass");
            _utxo = utxosBis;
        }
        else    {
            return null;
        }
        Triple<ArrayList<MyTransactionOutPoint>, ArrayList<TransactionOutput>, ArrayList<UTXO>> set1 = boltzmannSet(_utxo, spendAmount, address, set0.getLeft(), account, set0.getMiddle());
        if(set1 == null)    {
            return null;
        }

        Pair<ArrayList<MyTransactionOutPoint>, ArrayList<TransactionOutput>> ret = Pair.of(new ArrayList<MyTransactionOutPoint>(), new ArrayList<TransactionOutput>());

        ret.getLeft().addAll(set0.getLeft());
        ret.getLeft().addAll(set1.getLeft());
//        ret.getRight().addAll(set0.getMiddle());
        ret.getRight().addAll(set1.getMiddle());

        return ret;
    }

    public Triple<ArrayList<MyTransactionOutPoint>, ArrayList<TransactionOutput>, ArrayList<UTXO>> boltzmannSet(List<UTXO> utxos, BigInteger spendAmount, String address, List<MyTransactionOutPoint> firstPassOutpoints, int account, List<TransactionOutput> outputs0) {

        if(utxos == null || utxos.size() == 0)    {
            return null;
        }

        List<String> seenPreviousSetHash = null;
        if(firstPassOutpoints != null)    {
            seenPreviousSetHash = new ArrayList<String>();

            for(MyTransactionOutPoint outpoint : firstPassOutpoints)   {
                seenPreviousSetHash.add(outpoint.getTxHash().toString());
            }
        }

        int changeType = 84;
        int mixedType = 84;
        if(PrefsUtil.getInstance(context).getValue(PrefsUtil.USE_LIKE_TYPED_CHANGE, true) == true)    {
            //
            // inputs are pre-grouped by type
            // type of address for change must match type of address for inputs
            //
            String utxoAddress = utxos.get(0).getOutpoints().get(0).getAddress();
            if(FormatsUtil.Companion.isValidBech32(utxoAddress))    {
                changeType = 84;
            }
            else if(Address.fromBase58(SentinelState.Companion.getNetworkParam(), utxoAddress).isP2SHAddress())   {
                changeType = 49;
            }
            else    {
                changeType = 44;
            }

            //
            // type of address for 'mixed' amount must match type of address for destination
            //
            if(FormatsUtil.Companion.isValidBech32(address))    {
                mixedType = 84;
            }
            else if(Address.fromBase58(SentinelState.Companion.getNetworkParam(), address).isP2SHAddress())   {
                mixedType = 49;
            }
            else    {
                mixedType = 44;
            }
        }

        Triple<Integer,Integer,Integer> firstPassOutpointTypes = null;
        if(firstPassOutpoints != null)    {
            firstPassOutpointTypes = FeeUtil.getInstance().getOutpointCount(new Vector<MyTransactionOutPoint>(firstPassOutpoints));
        }
        else    {
            firstPassOutpointTypes = Triple.of(0, 0, 0);
        }

        long totalOutpointsAmount = 0L;
        for(UTXO utxo : utxos)   {
            totalOutpointsAmount += utxo.getValue();
        }
        Timber.d("total outputs amount:" + totalOutpointsAmount);
        Timber.d( "spend amount:" + spendAmount.toString());
        Timber.d("utxos:" + utxos.size());

        if(totalOutpointsAmount <= spendAmount.longValue())    {
            Timber.d( "spend amount must be > total amount available");
            return null;
        }

        List<MyTransactionOutPoint> selectedOutpoints = new ArrayList<MyTransactionOutPoint>();
        BigInteger selectedValue = BigInteger.ZERO;
        BigInteger biFee = BigInteger.ZERO;
        List<TransactionOutput> txOutputs = new ArrayList<TransactionOutput>();
        TransactionOutput txSpendOutput = null;
        TransactionOutput txChangeOutput = null;
        Script outputScript = null;
        String changeAddress = null;
        HashMap<String,MyTransactionOutPoint> seenOutpoints = new HashMap<String,MyTransactionOutPoint>();
        List<MyTransactionOutPoint> recycleOutPoints = new ArrayList<MyTransactionOutPoint>();
        List<UTXO> recycleUTXOs = new ArrayList<UTXO>();

        BigInteger bDust = firstPassOutpoints == null ? BigInteger.ZERO : SentinelState.Companion.getBDust();

        // select utxos until > spendAmount * 2
        // create additional change output(s)
        int idx = 0;
        for (int i = 0; i < utxos.size(); i++) {

            UTXO utxo = utxos.get(i);

            boolean utxoIsSelected = false;

            recycleOutPoints.clear();

            for(MyTransactionOutPoint op : utxo.getOutpoints())   {
                String hash = op.getTxHash().toString();
                if(seenPreviousSetHash != null && seenPreviousSetHash.contains(hash))    {
                    ;
                }
                else if(!seenOutpoints.containsKey(hash))    {
                    seenOutpoints.put(hash,op);
                    selectedValue = selectedValue.add(BigInteger.valueOf(op.getValue().longValue()));
                    Timber.d( "selected:" + i + "," + op.getTxHash().toString() + "," + op.getValue().longValue());
                    utxoIsSelected = true;
                }
                else if(op.getValue().longValue() > seenOutpoints.get(hash).getValue().longValue()) {
                    recycleOutPoints.add(seenOutpoints.get(hash));
                    seenOutpoints.put(hash,op);
                    selectedValue = selectedValue.subtract(BigInteger.valueOf(seenOutpoints.get(hash).getValue().longValue()));
                    selectedValue = selectedValue.add(BigInteger.valueOf(op.getValue().longValue()));
                    Timber.d("selected (replace):"+ i + "," + op.getTxHash().toString() + "," + op.getValue().longValue());
                    utxoIsSelected = true;
                }
                else    {
                    ;
                }

                selectedOutpoints.clear();
                selectedOutpoints.addAll(seenOutpoints.values());
            }

            if(recycleOutPoints.size() > 0)    {
                UTXO recycleUTXO = new UTXO();
                recycleUTXO.setOutpoints(recycleOutPoints);
                recycleUTXOs.add(recycleUTXO);
            }

            if(utxoIsSelected)    {
                idx++;
            }

            if(firstPassOutpoints != null)    {
                Triple<Integer,Integer,Integer> outputTypes = FeeUtil.getInstance().getOutpointCount(new Vector<MyTransactionOutPoint>(selectedOutpoints));
                biFee = FeeUtil.getInstance().estimatedFeeSegwit(firstPassOutpointTypes.getLeft() + outputTypes.getLeft(), firstPassOutpointTypes.getMiddle() + outputTypes.getMiddle(), firstPassOutpointTypes.getRight() + outputTypes.getRight(), 4);
            }

            if(selectedValue.compareTo(spendAmount.add(biFee).add(bDust)) > 0)    {
                break;
            }

        }

        if(selectedValue.compareTo(spendAmount.add(biFee).add(bDust)) <= 0)    {
            return null;
        }

        List<MyTransactionOutPoint> _selectedOutpoints = new ArrayList<MyTransactionOutPoint>();
        Collections.sort(selectedOutpoints, new UTXO.OutpointComparator());
        long _value = 0L;
        for(MyTransactionOutPoint op : selectedOutpoints)   {
            _selectedOutpoints.add(op);
            _value += op.getValue().longValue();
            if(firstPassOutpoints != null)    {
                Triple<Integer,Integer,Integer> outputTypes = FeeUtil.getInstance().getOutpointCount(new Vector<MyTransactionOutPoint>(_selectedOutpoints));
                biFee = FeeUtil.getInstance().estimatedFeeSegwit(firstPassOutpointTypes.getLeft() + outputTypes.getLeft(), firstPassOutpointTypes.getMiddle() + outputTypes.getMiddle(), firstPassOutpointTypes.getRight() + outputTypes.getRight(), 4);
            }
            if(_value > spendAmount.add(biFee).add(bDust).longValue())    {
                break;
            }
        }
        selectedValue = BigInteger.valueOf(_value);
        selectedOutpoints.clear();
        selectedOutpoints.addAll(_selectedOutpoints);

        Timber.d( "utxos idx:" + idx);

        List<UTXO> _utxos = new ArrayList<>(utxos.subList(idx, utxos.size()));
        Timber.d( "utxos after selection:" + _utxos.size());
        _utxos.addAll(recycleUTXOs);
        Timber.d( "utxos after adding recycled:" + _utxos.size());
        BigInteger changeDue = selectedValue.subtract(spendAmount);

        if(firstPassOutpoints != null)    {
            Triple<Integer,Integer,Integer> outputTypes = FeeUtil.getInstance().getOutpointCount(new Vector<MyTransactionOutPoint>(selectedOutpoints));
            biFee = FeeUtil.getInstance().estimatedFeeSegwit(firstPassOutpointTypes.getLeft() + outputTypes.getLeft(), firstPassOutpointTypes.getMiddle() + outputTypes.getMiddle(), firstPassOutpointTypes.getRight() + outputTypes.getRight(), 4);
            Timber.d( "biFee:" + biFee.toString());
            if(biFee.mod(BigInteger.valueOf(2L)).compareTo(BigInteger.ZERO) != 0)    {
                biFee = biFee.add(BigInteger.ONE);
            }
            Timber.d( "biFee pair:" + biFee.toString());
        }

        if(changeDue.subtract(biFee.divide(BigInteger.valueOf(2L))).compareTo(SentinelState.Companion.getBDust()) > 0)    {
            changeDue = changeDue.subtract(biFee.divide(BigInteger.valueOf(2L)));
            Timber.d( "fee set1:" + biFee.divide(BigInteger.valueOf(2L)).toString());
        }
        else    {
            return null;
        }

        if(outputs0 != null && outputs0.size() == 2)    {
            TransactionOutput changeOutput0 = outputs0.get(1);
            BigInteger changeDue0 = BigInteger.valueOf(changeOutput0.getValue().longValue());
            if(changeDue0.subtract(biFee.divide(BigInteger.valueOf(2L))).compareTo(SentinelState.Companion.getBDust()) > 0)    {
                changeDue0 = changeDue0.subtract(biFee.divide(BigInteger.valueOf(2L)));
                Timber.d( "fee set0:" + biFee.divide(BigInteger.valueOf(2L)).toString());
            }
            else    {
                return null;
            }
            changeOutput0.setValue(Coin.valueOf(changeDue0.longValue()));
            outputs0.set(1, changeOutput0);
        }

        try {

            String _address = null;
            if(firstPassOutpoints == null)    {
                _address = address;
            }
            else    {
                _address = getChangeAddress(mixedType, account);
            }
            if(FormatsUtil.Companion.isValidBech32(_address))   {
                txSpendOutput = Bech32Util.getInstance().getTransactionOutput(_address, spendAmount.longValue());
            }
            else    {
                outputScript = ScriptBuilder.createOutputScript(org.bitcoinj.core.Address.fromBase58(SentinelState.Companion.getNetworkParam(), _address));
                txSpendOutput = new TransactionOutput(SentinelState.Companion.getNetworkParam(), null, Coin.valueOf(spendAmount.longValue()), outputScript.getProgram());
            }
            txOutputs.add(txSpendOutput);

            changeAddress = getChangeAddress(changeType, account);
            if(FormatsUtil.Companion.isValidBech32(changeAddress))    {
                txChangeOutput = Bech32Util.getInstance().getTransactionOutput(changeAddress, changeDue.longValue());
            }
            else    {
                outputScript = ScriptBuilder.createOutputScript(org.bitcoinj.core.Address.fromBase58(SentinelState.Companion.getNetworkParam(), changeAddress));
                txChangeOutput = new TransactionOutput(SentinelState.Companion.getNetworkParam(), null, Coin.valueOf(changeDue.longValue()), outputScript.getProgram());
            }
            txOutputs.add(txChangeOutput);
        }
        catch(Exception e) {
            return null;
        }

        long inValue = 0L;
        for(MyTransactionOutPoint outpoint : selectedOutpoints)   {
            inValue += outpoint.getValue().longValue();
            Timber.d( "input:" + outpoint.getTxHash().toString() + "-" + outpoint.getTxOutputN() + "," + outpoint.getValue().longValue());
        }
        long outValue = 0L;
        for(TransactionOutput tOut : txOutputs)   {
            outValue += tOut.getValue().longValue();
            Timber.d( "output:" + tOut.toString() + "," + tOut.getValue().longValue());
        }

        Triple<ArrayList<MyTransactionOutPoint>, ArrayList<TransactionOutput>, ArrayList<UTXO>> ret = Triple.of(new ArrayList<MyTransactionOutPoint>(), new ArrayList<TransactionOutput>(), new ArrayList<UTXO>());
        ret.getLeft().addAll(selectedOutpoints);
        ret.getMiddle().addAll(txOutputs);
        if(outputs0 != null)    {
            ret.getMiddle().addAll(outputs0);
        }
        ret.getRight().addAll(_utxos);

        outValue += biFee.longValue();

        Timber.d( "inputs:" + inValue);
        Timber.d( "outputs:" + outValue);

        return ret;

    }

    private String getChangeAddress(int type, int account)    {

        if(type != 44 || PrefsUtil.getInstance(context).getValue(PrefsUtil.USE_LIKE_TYPED_CHANGE, true) == false)    {
            ;
        }
        else    {
            type = 44;
        }
//
//        if(account == WhirlpoolMeta.getInstance(context).getWhirlpoolPostmix())    {
//            int idx = AddressFactory.getInstance(context).getHighestPostChangeIdx();
//            String change_address = BIP84Util.getInstance(context).getAddressAt(WhirlpoolMeta.getInstance(context).getWhirlpoolPostmix(), AddressFactory.CHANGE_CHAIN, idx).getBech32AsString();
//            AddressFactory.getInstance(context).setHighestPostChangeIdx(idx + 1);
//            return change_address;
//        }
//        else if(type == 84)    {
//            String change_address = BIP84Util.getInstance(context).getAddressAt(AddressFactory.CHANGE_CHAIN, BIP84Util.getInstance(context).getWallet().getAccount(account).getChange().getAddrIdx()).getBech32AsString();
//            BIP84Util.getInstance(context).getWallet().getAccount(account).getChange().incAddrIdx();
//            return change_address;
//        }
//        else if(type == 49)    {
//            String change_address = BIP49Util.getInstance(context).getAddressAt(AddressFactory.CHANGE_CHAIN, BIP49Util.getInstance(context).getWallet().getAccount(0).getChange().getAddrIdx()).getAddressAsString();
//            BIP49Util.getInstance(context).getWallet().getAccount(0).getChange().incAddrIdx();
//            return change_address;
//        }
//        else    {
            try {
                String change_address = HD_WalletFactory.getInstance(context).get().getAccount(0).getChange().getAddressAt(HD_WalletFactory.getInstance(context).get().getAccount(0).getChange().getAddrIdx()).getAddressString();
                HD_WalletFactory.getInstance(context).get().getAccount(0).getChange().incAddrIdx();
                return change_address;
            }
            catch(IOException ioe) {
                return null;
            }
            catch(MnemonicException.MnemonicLengthException mle) {
                return null;
            }
//        }

    }

    public static ECKey getPrivKey(String address, int account)    {

//        Timber.d( "get privkey for:" + address);

        ECKey ecKey = null;

//        try {
//            String path = APIFactory.getInstance(context).getUnspentPaths().get(address);
//            Timber.d( "address path:" + path);
//            if(path != null)    {
//                String[] s = path.split("/");
//                if(FormatsUtil.getInstance().isValidBech32(address))    {
//                    Timber.d( "address type:" + "bip84");
//                    HD_Address addr = null;
//                    if(account == 0)    {
//                        addr = BIP84Util.getInstance(context).getWallet().getAccount(account).getChain(Integer.parseInt(s[1])).getAddressAt(Integer.parseInt(s[2]));
//                    }
//                    else    {
//                        addr = BIP84Util.getInstance(context).getWallet().getAccountAt(account).getChain(Integer.parseInt(s[1])).getAddressAt(Integer.parseInt(s[2]));
//                    }
//                    ecKey = addr.getECKey();
//                }
//                else if(Address.fromBase58(SamouraiWallet.getInstance().getCurrentNetworkParams(), address).isP2SHAddress())    {
//                    Timber.d( "address type:" + "bip49");
//                    HD_Address addr = BIP49Util.getInstance(context).getWallet().getAccount(0).getChain(Integer.parseInt(s[1])).getAddressAt(Integer.parseInt(s[2]));
//                    ecKey = addr.getECKey();
//                }
//                else    {
//                    Timber.d( "address type:" + "bip44");
//                    int account_no = APIFactory.getInstance(context).getUnspentAccounts().get(address);
//                    HD_Address hd_address = AddressFactory.getInstance(context).get(account_no, Integer.parseInt(s[1]), Integer.parseInt(s[2]));
//                    String strPrivKey = hd_address.getPrivateKeyString();
//                    DumpedPrivateKey pk = new DumpedPrivateKey(SamouraiWallet.getInstance().getCurrentNetworkParams(), strPrivKey);
//                    ecKey = pk.getKey();
//                }
//            }
//            else    {
//                Timber.d( "address type:" + "bip47");
//                Timber.d( "address:" + address);
//                String pcode = BIP47Meta.getInstance().getPCode4Addr(address);
//                Timber.d( "pcode:" + pcode);
//                int idx = BIP47Meta.getInstance().getIdx4Addr(address);
//                PaymentAddress addr = BIP47Util.getInstance(context).getReceiveAddress(new PaymentCode(pcode), idx);
//                ecKey = addr.getReceiveECKey();
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//            return null;
//        }

        return ecKey;
    }



}
