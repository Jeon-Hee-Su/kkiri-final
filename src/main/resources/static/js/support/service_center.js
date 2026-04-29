document.addEventListener('DOMContentLoaded', () => {
	const chatWindow = document.getElementById("chat-window");
	const messageContainer = document.getElementById("message-container");
	const chatInput = document.getElementById("chat-input");
	const sendBtn = document.getElementById("sendBtn");
	
	const appendMessage = (role, text) => {
	    const isUser = role === 'user';
	    const messageHtml = isUser ? `
	        <div class="flex items-start gap-4 max-w-3xl ml-auto flex-row-reverse mb-6">
	            <div class="w-10 h-10 rounded-full bg-slate-200 overflow-hidden shrink-0 border border-slate-300">
	                <img alt="User" src="/img/default_profile.jpg" class="w-full h-full object-cover" />
	            </div>
	            <div class="bg-blue-600 text-white p-4 rounded-2xl rounded-tr-none shadow-sm">
	                <p class="text-sm">${text}</p>
	            </div>
	        </div>` : `
	        <div class="flex items-start gap-4 mb-6">
	            <div class="w-10 h-10 rounded-full bg-primary/10 flex items-center justify-center shrink-0 border border-primary/20">
	                <span class="material-symbols-outlined text-primary text-xl">smart_toy</span>
	            </div>
	            <div class="space-y-1">
	                <p class="text-xs font-semibold text-slate-500 ml-1">고객 센터 봇</p>
	                <div class="bg-white dark:bg-slate-800 p-4 rounded-2xl rounded-tl-none border border-slate-200 shadow-sm">
	                    <p class="text-sm leading-relaxed">${text}</p>
	                </div>
	            </div>
	        </div>`;
		
		messageContainer.insertAdjacentHTML('beforeend', messageHtml);
		chatWindow.scrollTop = chatWindow.scrollHeight;
	};
		
	const sendMessage = async () => {
		const message = chatInput.value.trim();
		if(!message){
			return;
		}
		
		appendMessage('user', message);
		chatInput.value = '';
		
		try {
			const response = await authFetch('/api/aiService/chat', {
				method: 'POST',
				headers: {
					'Content-Type': 'application/json'
				},
				body: JSON.stringify({message: message})
			});
			
			const data = await response.json();
			appendMessage('bot', data.reply);
		} catch(error) {
			appendMessage('bot', '데이터를 가져오는 중 오류가 발생했습니다.')
		}
	}
	sendBtn.addEventListener('click', sendMessage);
	
	chatInput.addEventListener('keypress', (e) => {
		if(e.key === 'Enter' && !e.shiftKey) {
			e.preventDefault();
			sendMessage();
		}
	});
	
});