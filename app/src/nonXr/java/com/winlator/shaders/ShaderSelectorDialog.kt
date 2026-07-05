package com.winlator.shaders

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.gamenative.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.io.File

class ShaderSelectorDialog : BottomSheetDialogFragment() {

    private lateinit var viewModel: ShaderViewModel
    private lateinit var shaderEffectManager: ShaderEffectManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchField: EditText
    private lateinit var loadingText: TextView
    private lateinit var enabledSwitch: Switch

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_shader_selector, container, false)

        recyclerView = view.findViewById(R.id.shaderRecyclerView)
        searchField = view.findViewById(R.id.shaderSearch)
        loadingText = view.findViewById(R.id.loadingText)
        enabledSwitch = view.findViewById(R.id.shaderEnabledSwitch)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = ShaderListAdapter(
            onShaderClick = { shader -> onShaderSelected(shader) },
            onFavoriteToggle = { path -> viewModel.toggleFavorite(path) }
        )

        viewModel = ViewModelProvider(this)[ShaderViewModel::class.java]
        viewModel.shaders.observe(viewLifecycleOwner) { shaders ->
            (recyclerView.adapter as ShaderListAdapter).submitList(shaders)
            loadingText.visibility = if (shaders.isNullOrEmpty()) View.VISIBLE else View.GONE
        }

        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                val filtered = viewModel.shaders.value?.filter {
                    it.name.contains(text, ignoreCase = true)
                }
                (recyclerView.adapter as ShaderListAdapter).submitList(filtered)
            }
        })

        enabledSwitch.isChecked = viewModel.getShaderEnabled()
        enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleShaderEnabled(isChecked)
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        shaderEffectManager = (requireActivity() as? ShaderSelectorHost)?.shaderEffectManager
            ?: throw IllegalStateException("Activity must implement ShaderSelectorHost")
        viewModel.loadShaders()

        val behavior = (dialog as? com.google.android.material.bottomsheet.BottomSheetDialog)?.behavior
        behavior?.peekHeight = 600
        behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun onShaderSelected(shader: ShaderEntry) {
        shaderEffectManager.onShaderSelected(shader)
        dismiss()
    }
}

interface ShaderSelectorHost {
    val shaderEffectManager: ShaderEffectManager?
}

